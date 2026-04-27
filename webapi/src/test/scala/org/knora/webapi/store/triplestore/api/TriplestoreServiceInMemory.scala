/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.api

import org.apache.jena.query.*
import org.apache.jena.rdf.model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
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
import zio.ZIO
import zio.ZLayer
import zio.stream.ZStream

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters.*

import org.knora.webapi.IRI
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.SparqlConstructResponse
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.slice.common.domain.InternalIri
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
import org.knora.webapi.util.ZScopedJavaIoStreams.fileInputStream
import org.knora.webapi.util.ZScopedJavaIoStreams.fileOutputStream

trait TestTripleStore extends TriplestoreService {
  def setDataset(ds: Dataset): UIO[Unit]
  def getDataset: UIO[Dataset]
  def printDataset(prefix: String = ""): UIO[Unit]
  def datasetStatements: Task[List[model.Statement]]
}

object TestTripleStore {

  def setDatasetFromTriG(triG: String) =
    TestDatasetBuilder.datasetFromTriG(triG).flatMap(TriplestoreServiceInMemory.setDataset)

  def setDataset(dataset: Dataset): ZIO[TestTripleStore, Throwable, Unit] =
    ZIO.serviceWithZIO[TestTripleStore](_.setDataset(dataset))

  def setEmptyDataset() =
    ZIO.serviceWithZIO[TestTripleStore](_.setDataset(TDB2Factory.createDataset()))

  def getDataset: RIO[TestTripleStore, Dataset] =
    ZIO.serviceWithZIO[TestTripleStore](_.getDataset)

  def printDataset(prefix: String = ""): RIO[TestTripleStore, Unit] =
    ZIO.serviceWithZIO[TestTripleStore](_.printDataset(prefix))
}

final case class TriplestoreServiceInMemory(datasetRef: Ref[Dataset])(implicit val sf: StringFormatter)
    extends TestTripleStore {

  // TDB2 stores transaction state in a ThreadLocal: begin / commit / abort / end
  // must all run on the same OS thread. ZIO.attemptBlocking pins the synchronous
  // body to a single blocking-pool thread for its full duration.
  // f must be plain Scala/Java and must fully materialise its result to the Dataset before returning
  private def withTx[A](rw: ReadWrite)(f: Dataset => A): Task[A] =
    getDataset.flatMap { ds =>
      ZIO.attemptBlocking {
        ds.begin(rw)
        var committed = false
        try {
          val a = f(ds)
          ds.commit()
          committed = true
          a
        } finally {
          if (!committed && ds.isInTransaction) ds.abort()
          if (ds.isInTransaction) ds.end()
        }
      }
    }

  override def query(query: Select): Task[SparqlSelectResult] =
    withTx(ReadWrite.READ) { ds =>
      val qExec = QueryExecutionFactory.create(query.sparql, ds)
      try {
        val rs = qExec.execSelect()
        try toSparqlSelectResult(rs)
        finally rs.close()
      } finally qExec.close()
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
      val node          = solution.get(key).asNode
      val value: String = // do not include datatype in string if node is a literal
        if (node.isLiteral) { node.getLiteralLexicalForm }
        else { node.toString }
      key -> value
    }.toMap
    VariableResultsRow(keyValueMap)
  }

  override def query(query: Ask): Task[Boolean] =
    withTx(ReadWrite.READ) { ds =>
      val qExec = QueryExecutionFactory.create(query.sparql, ds)
      try qExec.execAsk()
      finally qExec.close()
    }

  override def query(query: Construct): Task[SparqlConstructResponse] =
    for {
      turtle   <- queryRdf(query)
      rdfModel <- ZIO
                    .attempt(RdfModel.fromTurtle(turtle))
                    .foldZIO(
                      _ =>
                        if (turtle.contains("##  Query cancelled due to timeout during execution")) {
                          ZIO.fail(TriplestoreTimeoutException("Triplestore timed out."))
                        } else {
                          ZIO.fail(TriplestoreResponseException("Couldn't parse Turtle from triplestore."))
                        },
                      ZIO.succeed(_),
                    )
    } yield SparqlConstructResponse.make(rdfModel)

  override def queryRdf(query: Construct): Task[String] = constructToTurtle(query.sparql)

  private def constructToTurtle(sparql: String): Task[String] =
    withTx(ReadWrite.READ) { ds =>
      val qExec = QueryExecutionFactory.create(sparql, ds)
      try {
        val m = qExec.execConstruct(ModelFactory.createDefaultModel())
        try {
          val os = new ByteArrayOutputStream()
          try {
            m.write(os, "TURTLE")
            os.toString(StandardCharsets.UTF_8)
          } finally os.close()
        } finally m.close()
      } finally qExec.close()
    }

  override def queryToFile(
    query: Construct,
    graphIri: InternalIri,
    outputFile: zio.nio.file.Path,
    outputFormat: QuadFormat,
  ): Task[Unit] =
    for {
      turtle <- constructToTurtle(query.sparql)
      _      <- ZIO.attempt(
             RdfFormatUtil.turtleToQuadsFile(
               RdfStringSource(turtle),
               graphIri.value,
               outputFile.toFile.toPath,
               outputFormat,
             ),
           )
    } yield ()

  override def query(query: Update): Task[Unit] =
    withTx(ReadWrite.WRITE) { ds =>
      val update    = UpdateFactory.create(query.sparql)
      val processor = UpdateExecutionFactory.create(update, ds)
      processor.execute()
    }

  override def downloadGraph(
    graphIri: InternalIri,
    outputFile: zio.nio.file.Path,
    outputFormat: QuadFormat,
  ): Task[Unit] = ZIO.scoped {
    for {
      fos <- fileOutputStream(outputFile)
      lang = RdfFormatUtil.rdfFormatToJenaParsingLang(outputFormat)
      _   <- withTx(ReadWrite.READ) { ds =>
             ds.getNamedModel(graphIri.value).write(fos, lang.getName)
           }
    } yield ()
  }

  override def resetTripleStoreContent(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean,
  ): Task[Unit] = for {
    _ <- dropDataGraphByGraph()
    _ <- insertDataIntoTriplestore(rdfDataObjects, prependDefaults)
  } yield ()

  override def dropDataGraphByGraph(): Task[Unit] = createEmptyDataset.flatMap(datasetRef.set(_)).unit

  override def insertDataIntoTriplestore(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean,
  ): Task[Unit] =
    getListToInsert(rdfDataObjects, prependDefaults).flatMap(ZIO.foreachDiscard(_)(insertRdfDataObject))

  private def getListToInsert(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean,
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
        _         <- withTx(ReadWrite.WRITE) { ds =>
               RDFDataMgr.read(ds.getNamedModel(graphName), in, Lang.TURTLE)
             }
      } yield ()
    }

  private def loadRdfUrl(path: String): ZIO[Any & Scope, Throwable, InputStream] =
    ZIO
      .attemptBlocking(
        Option(getClass.getClassLoader.getResourceAsStream(path)).getOrElse(throw new Exception("can't find resource")),
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
  override def getDataset: UIO[Dataset]           =
    datasetRef.get

  override def printDataset(prefix: String = ""): UIO[Unit] =
    for {
//      _  <- Console.printLine(s"TDB Context:\n${TDB.getContext}\n").orDie
      ds <- getDataset
      _  <- Console.printLine(s"${prefix}TriplestoreServiceInMemory.printDataset:").orDie
      _   = printDatasetContents(ds)
    } yield ()

  override def datasetStatements: Task[List[model.Statement]] =
    withTx(ReadWrite.READ)(ds => ds.getUnionModel.listStatements.toList.asScala.toList)

  def printDatasetContents(dataset: Dataset): Unit = {
    // Iterate over the named models
    dataset.begin(ReadWrite.READ)
    val names = dataset.listNames()

    while (names.hasNext) {
      val name  = names.next()
      val model = dataset.getNamedModel(name)

      println(s"Graph: $name\n")
      // Write the model in Turtle format
      RDFDataMgr.write(System.out, model, Lang.TURTLE)
    }
    dataset.end()
  }

  private val notImplemented = ZIO.die(new UnsupportedOperationException("Not implemented yet."))

  override def downloadRepository(outputFile: Path, graphs: GraphsForMigration): Task[Unit] =
    notImplemented

  override def uploadRepository(inputFile: Path): Task[Unit] =
    notImplemented

  override def uploadNQuads(stream: ZStream[Any, Throwable, Byte]): Task[Unit] =
    notImplemented

  override def dropGraph(graphName: IRI): Task[Unit] =
    notImplemented

  override def compact(): Task[Boolean] = ZIO.succeed(false)
}

object TriplestoreServiceInMemory {

  def setDataset(dataset: Dataset): ZIO[TestTripleStore, Throwable, Unit] =
    ZIO.serviceWithZIO[TestTripleStore](_.setDataset(dataset))

  def getDataset: RIO[TestTripleStore, Dataset] =
    ZIO.serviceWithZIO[TestTripleStore](_.getDataset)

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
    ZIO
      .succeed(TDB.getContext.set(TDB.symUnionDefaultGraph, true))
      .as(TDB2Factory.createDataset())

  val emptyDatasetRefLayer: ULayer[Ref[Dataset]] = ZLayer.fromZIO(createEmptyDataset.flatMap(Ref.make(_)))

  val layer: ZLayer[Ref[Dataset] & StringFormatter, Nothing, TriplestoreServiceInMemory] =
    ZLayer.derive[TriplestoreServiceInMemory]

  val emptyLayer = emptyDatasetRefLayer >>> layer
}
