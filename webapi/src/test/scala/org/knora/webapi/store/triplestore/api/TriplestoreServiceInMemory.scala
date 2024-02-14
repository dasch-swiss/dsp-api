/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.api
import org.apache.jena.query.*
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.tdb.TDB
import org.apache.jena.tdb2.TDB2Factory
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import zio.Console
import zio.RIO
import zio.Ref
import zio.Scope
import zio.Task
import zio.UIO
import zio.ULayer
import zio.URIO
import zio.ZIO
import zio.ZLayer
import zio.macros.accessible

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.IteratorHasAsScala

import org.knora.webapi.IRI
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.SparqlConstructResponse
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.TestDatasetBuilder
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory.createEmptyDataset
import org.knora.webapi.store.triplestore.defaults.DefaultRdfData
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus.Available
import org.knora.webapi.store.triplestore.errors.TriplestoreResponseException
import org.knora.webapi.store.triplestore.errors.TriplestoreTimeoutException
import org.knora.webapi.store.triplestore.errors.TriplestoreUnsupportedFeatureException
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.util.ZScopedJavaIoStreams.byteArrayOutputStream
import org.knora.webapi.util.ZScopedJavaIoStreams.fileInputStream
import org.knora.webapi.util.ZScopedJavaIoStreams.fileOutputStream

@accessible
trait TestTripleStore extends TriplestoreService {
  def setDataset(ds: Dataset): UIO[Unit]
  def getDataset: UIO[Dataset]
  def printDataset: UIO[Unit]
}

final case class TriplestoreServiceInMemory(datasetRef: Ref[Dataset], implicit val sf: StringFormatter)
    extends TestTripleStore {

  override def query(query: Select): Task[SparqlSelectResult] = {
    require(!query.isGravsearch, "`isGravsearch` parameter is not supported by fake implementation yet")
    ZIO.scoped(execSelect(query.sparql).map(toSparqlSelectResult))
  }

  private def execSelect(query: String): ZIO[Any & Scope, Throwable, ResultSet] = {
    def executeQuery(qExec: QueryExecution) = ZIO.attempt(qExec.execSelect)
    def closeResultSet(rs: ResultSet)       = ZIO.succeed(rs.close())
    getReadTransactionQueryExecution(query).flatMap(qExec => ZIO.acquireRelease(executeQuery(qExec))(closeResultSet))
  }

  private def getReadTransactionQueryExecution(query: String): ZIO[Scope, Throwable, QueryExecution] = {
    def acquire(query: String, ds: Dataset)                     = ZIO.attempt(QueryExecutionFactory.create(query, ds))
    def release(qExec: QueryExecution): ZIO[Any, Nothing, Unit] = ZIO.succeed(qExec.close())
    getDataSetWithTransaction(ReadWrite.READ).flatMap(ds => ZIO.acquireRelease(acquire(query, ds))(release))
  }

  private def getDataSetWithTransaction(readWrite: ReadWrite): URIO[Scope, Dataset] = {
    val acquire = getDataset.tap(ds => ZIO.succeed(ds.begin(readWrite)))
    def release(ds: Dataset) = ZIO.succeed(try { ds.commit() }
    finally { ds.end() })
    ZIO.acquireRelease(acquire)(release)
  }

  private def toSparqlSelectResult(resultSet: ResultSet): SparqlSelectResult = {
    val header     = SparqlSelectResultHeader(resultSet.getResultVars.asScala.toList)
    val resultBody = getSelectResultBody(resultSet)
    SparqlSelectResult(header, resultBody)
  }

  private def getSelectResultBody(resultSet: ResultSet): SparqlSelectResultBody = {
    val rows: Seq[VariableResultsRow] = resultSet.asScala
      .foldRight(List.empty[VariableResultsRow])((solution, list) => list.prepended(asVariableResultsRow(solution)))
    SparqlSelectResultBody(rows)
  }

  private def asVariableResultsRow(solution: QuerySolution): VariableResultsRow = {
    val keyValueMap = solution.varNames.asScala.map { key =>
      val node = solution.get(key).asNode
      val value: String = // do not include datatype in string if node is a literal
        if (node.isLiteral) { node.getLiteralLexicalForm }
        else { node.toString }
      key -> value
    }.toMap
    VariableResultsRow(keyValueMap)
  }

  override def query(query: Ask): Task[Boolean] =
    ZIO.scoped(getReadTransactionQueryExecution(query.sparql).map(_.execAsk()))

  override def query(query: Construct): Task[SparqlConstructResponse] =
    for {
      turtle <- queryRdf(query)
      rdfModel <- ZIO
                    .attempt(RdfFormatUtil.parseToRdfModel(turtle, Turtle))
                    .foldZIO(
                      _ =>
                        if (turtle.contains("##  Query cancelled due to timeout during execution")) {
                          ZIO.fail(TriplestoreTimeoutException("Triplestore timed out."))
                        } else {
                          ZIO.fail(TriplestoreResponseException("Couldn't parse Turtle from triplestore."))
                        },
                      ZIO.succeed(_)
                    )
    } yield SparqlConstructResponse.make(rdfModel)

  override def queryRdf(query: Construct): Task[String] = ZIO.scoped(execConstruct(query.sparql).flatMap(modelToTurtle))

  private def execConstruct(query: String): ZIO[Any & Scope, Throwable, Model] = {
    def executeQuery(qExec: QueryExecution) = ZIO.attempt(qExec.execConstruct(ModelFactory.createDefaultModel()))
    def closeModel(model: Model)            = ZIO.succeed(model.close())
    getReadTransactionQueryExecution(query).flatMap(qExec => ZIO.acquireRelease(executeQuery(qExec))(closeModel))
  }

  private def modelToTurtle(model: Model): ZIO[Any & Scope, Throwable, String] =
    for {
      os    <- byteArrayOutputStream()
      _      = model.write(os, "TURTLE")
      turtle = os.toString(StandardCharsets.UTF_8)
    } yield turtle

  override def queryToFile(
    query: Construct,
    graphIri: InternalIri,
    outputFile: zio.nio.file.Path,
    outputFormat: QuadFormat
  ): Task[Unit] = ZIO.scoped {
    for {
      model  <- execConstruct(query.sparql)
      source <- modelToTurtle(model).map(RdfStringSource)
      _      <- ZIO.attempt(RdfFormatUtil.turtleToQuadsFile(source, graphIri.value, outputFile.toFile.toPath, outputFormat))
    } yield ()
  }

  override def query(query: Update): Task[Unit] = {
    def doUpdate(ds: Dataset) = ZIO.attempt {
      val update    = UpdateFactory.create(query.sparql)
      val processor = UpdateExecutionFactory.create(update, ds)
      processor.execute()
    }
    ZIO.scoped(getDataSetWithTransaction(ReadWrite.WRITE).flatMap(doUpdate)).unit
  }

  override def downloadGraph(
    graphIri: InternalIri,
    outputFile: zio.nio.file.Path,
    outputFormat: QuadFormat
  ): Task[Unit] = ZIO.scoped {
    for {
      fos <- fileOutputStream(outputFile)
      ds  <- getDataset
      lang = RdfFormatUtil.rdfFormatToJenaParsingLang(outputFormat)
      _ <- ZIO.attemptBlocking {
             ds.begin(ReadWrite.READ)
             try { ds.getNamedModel(graphIri.value).write(fos, lang.getName) }
             finally { ds.end() }
           }
    } yield ()
  }

  override def resetTripleStoreContent(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean
  ): Task[Unit] = for {
    _ <- dropDataGraphByGraph()
    _ <- insertDataIntoTriplestore(rdfDataObjects, prependDefaults)
  } yield ()

  override def dropDataGraphByGraph(): Task[Unit] = createEmptyDataset.flatMap(datasetRef.set(_)).unit

  override def insertDataIntoTriplestore(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean
  ): Task[Unit] =
    getListToInsert(rdfDataObjects, prependDefaults).flatMap(ZIO.foreachDiscard(_)(insertRdfDataObject))

  private def getListToInsert(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean
  ): Task[List[RdfDataObject]] = {
    val listToInsert = if (prependDefaults) { DefaultRdfData.data.toList ::: rdfDataObjects }
    else { rdfDataObjects }
    if (listToInsert.isEmpty) {
      ZIO.fail(new IllegalArgumentException("List to insert is empty."))
    } else {
      ZIO.succeed(listToInsert)
    }
  }

  private def insertRdfDataObject(elem: RdfDataObject): ZIO[Any, Throwable, Unit] =
    ZIO.scoped {
      for {
        graphName <- checkGraphName(elem)
        in        <- loadRdfUrl(elem.path)
        ds        <- getDataSetWithTransaction(ReadWrite.WRITE)
        model      = ds.getNamedModel(graphName)
        _          = model.read(in, null, "TURTLE")
      } yield ()
    }

  private def loadRdfUrl(path: String): ZIO[Any & Scope, Throwable, InputStream] =
    ZIO
      .attemptBlocking(
        Option(getClass.getClassLoader.getResourceAsStream(path)).getOrElse(throw new Exception("can't find resource"))
      )
      .orElse(fileInputStream(Paths.get("..", path)))

  private def checkGraphName(elem: RdfDataObject): Task[String] =
    checkGraphName(elem.name)

  private def checkGraphName(graphName: IRI) =
    if (graphName == "default") {
      ZIO.fail(new TriplestoreUnsupportedFeatureException("Requests to the default graph are not supported."))
    } else {
      ZIO.succeed(graphName)
    }

  override def checkTriplestore(): Task[TriplestoreStatus] = ZIO.succeed(Available)

  override def setDataset(ds: Dataset): UIO[Unit] = datasetRef.set(ds)
  override def getDataset: UIO[Dataset] =
    datasetRef.get

  override def printDataset: UIO[Unit] =
    for {
      _  <- Console.printLine(s"TDB Context:\n${TDB.getContext}\n").orDie
      ds <- getDataset
      _  <- Console.printLine(s"TriplestoreServiceInMemory.printDataset:").orDie
      _   = printDatasetContents(ds)
    } yield ()

  def printDatasetContents(dataset: Dataset): Unit = {
    // Iterate over the named models
    dataset.begin(ReadWrite.READ)
    val names = dataset.listNames()

    while (names.hasNext) {
      val name  = names.next()
      val model = dataset.getNamedModel(name)

      println(s"Graph: $name\n")
      // Write the model in Turtle format
      RDFDataMgr.write(System.out, model, org.apache.jena.riot.Lang.TURTLE)
    }
    dataset.end()
  }

  private val notImplemented = ZIO.die(new UnsupportedOperationException("Not implemented yet."))

  override def downloadRepository(outputFile: Path, graphs: GraphsForMigration): Task[Unit] =
    notImplemented

  override def uploadRepository(inputFile: Path): Task[Unit] =
    notImplemented

  override def dropGraph(graphName: IRI): Task[Unit] =
    notImplemented

}

object TriplestoreServiceInMemory {

  def setDataset(dataset: Dataset): ZIO[TestTripleStore, Throwable, Unit] =
    ZIO.serviceWithZIO[TestTripleStore](_.setDataset(dataset))

  def getDataset: RIO[TestTripleStore, Dataset] =
    ZIO.serviceWithZIO[TestTripleStore](_.getDataset)

  def printDataset: RIO[TestTripleStore, Unit] =
    ZIO.serviceWithZIO[TestTripleStore](_.printDataset)

  def setDataSetFromTriG(triG: String): ZIO[TestTripleStore, Throwable, Unit] = TestDatasetBuilder
    .datasetFromTriG(triG)
    .flatMap(TriplestoreServiceInMemory.setDataset)

  /**
   * Creates an empty TBD2 [[Dataset]].
   *
   * Currently does not (yet) support create a [[Dataset]] which supports Lucene indexing.
   * TODO: https://jena.apache.org/documentation/query/text-query.html#configuration-by-code
   */
  val createEmptyDataset: UIO[Dataset] =
    ZIO.succeed(TDB2Factory.createDataset())

  val emptyDatasetRefLayer: ULayer[Ref[Dataset]] = ZLayer.fromZIO(createEmptyDataset.flatMap(Ref.make(_)))

  val layer: ZLayer[Ref[Dataset] & StringFormatter, Nothing, TriplestoreServiceInMemory] =
    ZLayer.fromFunction(TriplestoreServiceInMemory.apply _)

  val emptyLayer = emptyDatasetRefLayer >>> layer
}
