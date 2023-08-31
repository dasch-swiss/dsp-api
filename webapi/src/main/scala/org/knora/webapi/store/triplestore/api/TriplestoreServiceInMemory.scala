/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.api
import org.apache.jena.query.Dataset
import org.apache.jena.query.QueryExecution
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ReadWrite
import org.apache.jena.query.ResultSet
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.tdb2.TDB2Factory
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import zio.Ref
import zio.Scope
import zio.Task
import zio.UIO
import zio.URIO
import zio.ZIO
import zio.ZLayer

import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.IteratorHasAsScala

import org.knora.webapi.IRI
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.CheckTriplestoreResponse
import org.knora.webapi.messages.store.triplestoremessages.NamedGraphDataResponse
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.SparqlConstructRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlConstructResponse
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse
import org.knora.webapi.messages.util.rdf.QuadFormat
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.messages.util.rdf.RdfFormatUtil
import org.knora.webapi.messages.util.rdf.RdfStringSource
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.SparqlSelectResultBody
import org.knora.webapi.messages.util.rdf.SparqlSelectResultHeader
import org.knora.webapi.messages.util.rdf.Turtle
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaFormatUtil
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory.createEmptyDataset
import org.knora.webapi.store.triplestore.defaults.DefaultRdfData
import org.knora.webapi.store.triplestore.errors.TriplestoreResponseException
import org.knora.webapi.store.triplestore.errors.TriplestoreTimeoutException
import org.knora.webapi.store.triplestore.errors.TriplestoreUnsupportedFeatureException
import org.knora.webapi.util.ZScopedJavaIoStreams.byteArrayOutputStream
import org.knora.webapi.util.ZScopedJavaIoStreams.fileInputStream
import org.knora.webapi.util.ZScopedJavaIoStreams.fileOutputStream

final case class TriplestoreServiceInMemory(datasetRef: Ref[Dataset], implicit val sf: StringFormatter)
    extends TriplestoreService {
  private val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()

  override def query(query: Select): Task[SparqlSelectResult] = {
    require(!query.isGravsearch, "`isGravsearch` parameter is not supported by fake implementation yet")
    ZIO.scoped(execSelect(query.sparql).map(toSparqlSelectResult))
  }

  private def execSelect(query: String): ZIO[Any with Scope, Throwable, ResultSet] = {
    def executeQuery(qExec: QueryExecution) = ZIO.attempt(qExec.execSelect)
    def closeResultSet(rs: ResultSet)       = ZIO.succeed(rs.close())
    getReadTransactionQueryExecution(query).flatMap(qExec => ZIO.acquireRelease(executeQuery(qExec))(closeResultSet))
  }

  private def getReadTransactionQueryExecution(query: String): ZIO[Any with Scope, Throwable, QueryExecution] = {
    def acquire(query: String, ds: Dataset)                     = ZIO.attempt(QueryExecutionFactory.create(query, ds))
    def release(qExec: QueryExecution): ZIO[Any, Nothing, Unit] = ZIO.succeed(qExec.close())
    getDataSetWithTransaction(ReadWrite.READ).flatMap(ds => ZIO.acquireRelease(acquire(query, ds))(release))
  }

  private def getDataSetWithTransaction(readWrite: ReadWrite): URIO[Any with Scope, Dataset] = {
    val acquire = datasetRef.get.tap(ds => ZIO.succeed(ds.begin(readWrite)))
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

  override def sparqlHttpConstruct(request: SparqlConstructRequest): Task[SparqlConstructResponse] =
    for {
      turtle <- ZIO.scoped(execConstruct(request.sparql).flatMap(modelToTurtle))
      rdfModel <- ZIO
                    .attempt(RdfFeatureFactory.getRdfFormatUtil().parseToRdfModel(turtle, Turtle))
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

  private def execConstruct(query: String): ZIO[Any with Scope, Throwable, Model] = {
    def executeQuery(qExec: QueryExecution) = ZIO.attempt(qExec.execConstruct(ModelFactory.createDefaultModel()))
    def closeModel(model: Model)            = ZIO.succeed(model.close())
    getReadTransactionQueryExecution(query).flatMap(qExec => ZIO.acquireRelease(executeQuery(qExec))(closeModel))
  }

  private def modelToTurtle(model: Model): ZIO[Any with Scope, Throwable, String] =
    for {
      os    <- byteArrayOutputStream()
      _      = model.write(os, "TURTLE")
      turtle = os.toString(StandardCharsets.UTF_8)
    } yield turtle

  override def sparqlHttpExtendedConstruct(
    request: SparqlExtendedConstructRequest
  ): Task[SparqlExtendedConstructResponse] =
    for {
      turtle   <- ZIO.scoped(execConstruct(request.sparql).flatMap(modelToTurtle))
      response <- SparqlExtendedConstructResponse.parseTurtleResponse(turtle)
    } yield response

  override def sparqlHttpConstructFile(
    sparql: String,
    graphIri: IRI,
    outputFile: Path,
    outputFormat: QuadFormat
  ): Task[Unit] = ZIO.scoped {
    for {
      model  <- execConstruct(sparql)
      turtle <- modelToTurtle(model)
      _ <- ZIO
             .attempt(
               rdfFormatUtil
                 .turtleToQuadsFile(
                   rdfSource = RdfStringSource(turtle),
                   graphIri = graphIri,
                   outputFile = outputFile,
                   outputFormat = outputFormat
                 )
             )
    } yield ()
  }

  override def sparqlHttpUpdate(query: String): Task[Unit] = {
    def doUpdate(ds: Dataset) = ZIO.attempt {
      val update    = UpdateFactory.create(query)
      val processor = UpdateExecutionFactory.create(update, ds)
      processor.execute()
    }
    ZIO.scoped(getDataSetWithTransaction(ReadWrite.WRITE).flatMap(doUpdate)).unit
  }

  override def sparqlHttpGraphFile(
    graphIri: IRI,
    outputFile: Path,
    outputFormat: QuadFormat
  ): Task[Unit] = ZIO.scoped {
    for {
      fos <- fileOutputStream(outputFile)
      ds  <- datasetRef.get
      lang = JenaFormatUtil.rdfFormatToJenaParsingLang(outputFormat)
      _ <- ZIO.attemptBlocking {
             ds.begin(ReadWrite.READ)
             try { ds.getNamedModel(graphIri).write(fos, lang.getName) }
             finally { ds.end() }
           }
    } yield ()
  }

  override def sparqlHttpGraphData(graphIri: IRI): Task[NamedGraphDataResponse] = ZIO.scoped {
    for {
      ds <- getDataSetWithTransaction(ReadWrite.READ)
      model <- ZIO
                 .fromOption(Option(ds.getNamedModel(graphIri)))
                 .orElseFail(TriplestoreResponseException(s"Triplestore returned no content for graph $graphIri."))
      turtle <- modelToTurtle(model)
    } yield NamedGraphDataResponse(turtle)
  }

  override def resetTripleStoreContent(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean
  ): Task[Unit] = for {
    _ <- dropAllTriplestoreContent()
    _ <- insertDataIntoTriplestore(rdfDataObjects, prependDefaults)
  } yield ()

  override def dropAllTriplestoreContent(): Task[Unit] = createEmptyDataset.flatMap(datasetRef.set(_)).unit

  override def dropDataGraphByGraph(): Task[Unit] = dropAllTriplestoreContent()

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

  private def insertRdfDataObject(elem: RdfDataObject): ZIO[Any, Throwable, Unit] = {
    val inputFile = Paths.get("..", elem.path)
    ZIO.scoped {
      for {
        graphName <- checkGraphName(elem)
        in        <- fileInputStream(inputFile)
        ds        <- getDataSetWithTransaction(ReadWrite.WRITE)
        model      = ds.getNamedModel(graphName)
        _          = model.read(in, null, "TURTLE")
      } yield ()
    }
  }

  private def checkGraphName(elem: RdfDataObject): Task[String] =
    checkGraphName(elem.name)

  private def checkGraphName(graphName: IRI) =
    if (graphName == "default") {
      ZIO.fail(new TriplestoreUnsupportedFeatureException("Requests to the default graph are not supported."))
    } else {
      ZIO.succeed(graphName)
    }

  override def checkTriplestore(): Task[CheckTriplestoreResponse] = ZIO.succeed(CheckTriplestoreResponse.Available)

  override def downloadRepository(outputFile: Path): Task[Unit] =
    ZIO.fail(new UnsupportedOperationException("Not implemented in TriplestoreServiceInMemory."))

  override def uploadRepository(inputFile: Path): Task[Unit] =
    ZIO.fail(new UnsupportedOperationException("Not implemented in TriplestoreServiceInMemory."))

  override def insertDataGraphRequest(turtle: String, graphName: String): Task[Unit] =
    ZIO.scoped {
      for {
        name <- checkGraphName(graphName)
        ds   <- getDataSetWithTransaction(ReadWrite.WRITE)
        _     = ds.getNamedModel(name).read(new StringReader(turtle), null, turtle)
      } yield ()
    }
}

object TriplestoreServiceInMemory {

  /**
   * Creates an empty TBD2 [[Dataset]].
   *
   * Currently does not (yet) support create a [[Dataset]] which supports Lucene indexing.
   * TODO: https://jena.apache.org/documentation/query/text-query.html#configuration-by-code
   */
  val createEmptyDataset: UIO[Dataset] = ZIO.succeed(TDB2Factory.createDataset())

  val layer: ZLayer[Ref[Dataset] with StringFormatter, Nothing, TriplestoreService] =
    ZLayer.fromFunction(TriplestoreServiceInMemory.apply _)
}
