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
import org.apache.jena.update.UpdateExecutionFactory
import org.apache.jena.update.UpdateFactory
import zio.IO
import zio.Ref
import zio.Scope
import zio.Task
import zio.UIO
import zio.URIO
import zio.ZIO
import zio.ZLayer
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Path
import scala.collection.mutable
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.IteratorHasAsScala
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Paths

import org.knora.webapi.IRI
import org.knora.webapi.messages.store.triplestoremessages.CheckTriplestoreResponse
import org.knora.webapi.messages.store.triplestoremessages.DropAllRepositoryContentACK
import org.knora.webapi.messages.store.triplestoremessages.DropDataGraphByGraphACK
import org.knora.webapi.messages.store.triplestoremessages.FileWrittenResponse
import org.knora.webapi.messages.store.triplestoremessages.InsertGraphDataContentResponse
import org.knora.webapi.messages.store.triplestoremessages.InsertTriplestoreContentACK
import org.knora.webapi.messages.store.triplestoremessages.NamedGraphDataResponse
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.RepositoryUploadedResponse
import org.knora.webapi.messages.store.triplestoremessages.ResetRepositoryContentACK
import org.knora.webapi.messages.store.triplestoremessages.SparqlAskResponse
import org.knora.webapi.messages.store.triplestoremessages.SparqlConstructRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlConstructResponse
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse
import org.knora.webapi.messages.store.triplestoremessages.SparqlUpdateResponse
import org.knora.webapi.messages.util.rdf.QuadFormat
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.messages.util.rdf.RdfFormatUtil
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.SparqlSelectResultBody
import org.knora.webapi.messages.util.rdf.SparqlSelectResultHeader
import org.knora.webapi.messages.util.rdf.Statement
import org.knora.webapi.messages.util.rdf.Turtle
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf.RdfStringSource
import org.knora.webapi.store.triplestore.errors.TriplestoreException
import org.knora.webapi.store.triplestore.errors.TriplestoreResponseException
import org.knora.webapi.store.triplestore.errors.TriplestoreTimeoutException
import org.knora.webapi.store.triplestore.TestDatasetBuilder
import org.knora.webapi.store.triplestore.defaults.DefaultRdfData
import org.knora.webapi.store.triplestore.errors.TriplestoreUnsupportedFeatureException

final case class TriplestoreServiceFake(datasetRef: Ref[Dataset], implicit val sf: StringFormatter)
    extends TriplestoreService {
  private val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()

  override def doSimulateTimeout(): UIO[SparqlSelectResult] = ZIO.die(
    TriplestoreTimeoutException(
      "The triplestore took too long to process a request. This can happen because the triplestore needed too much time to search through the data that is currently in the triplestore. Query optimisation may help."
    )
  )

  override def sparqlHttpSelect(
    sparql: String,
    simulateTimeout: Boolean,
    isGravsearch: Boolean
  ): UIO[SparqlSelectResult] = {
    require(!simulateTimeout, "`simulateTimeout` parameter is not supported by fake implementation yet")
    require(!isGravsearch, "`isGravsearch` parameter is not supported by fake implementation yet")

    ZIO.scoped(execSelect(sparql).map(toSparqlSelectResult)).orDie
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

  override def sparqlHttpAsk(query: String): UIO[SparqlAskResponse] =
    ZIO.scoped(getReadTransactionQueryExecution(query).map(_.execAsk())).map(SparqlAskResponse).orDie

  override def sparqlHttpConstruct(request: SparqlConstructRequest): UIO[SparqlConstructResponse] = {
    def parseTurtleResponse(
      turtleStr: String
    ): IO[TriplestoreException, SparqlConstructResponse] = {
      val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()
      ZIO.attemptBlocking {
        val rdfModel: RdfModel                                 = rdfFormatUtil.parseToRdfModel(rdfStr = turtleStr, rdfFormat = Turtle)
        val statementMap: mutable.Map[IRI, Seq[(IRI, String)]] = mutable.Map.empty
        for (st: Statement <- rdfModel) {
          val subjectIri   = st.subj.stringValue
          val predicateIri = st.pred.stringValue
          val objectIri    = st.obj.stringValue
          val currentStatementsForSubject: Seq[(IRI, String)] =
            statementMap.getOrElse(subjectIri, Vector.empty[(IRI, String)])
          statementMap += (subjectIri -> (currentStatementsForSubject :+ (predicateIri, objectIri)))
        }
        SparqlConstructResponse(statementMap.toMap)
      }.foldZIO(
        _ =>
          if (turtleStr.contains("##  Query cancelled due to timeout during execution")) {
            ZIO.fail(TriplestoreTimeoutException("Triplestore timed out."))
          } else {
            ZIO.fail(TriplestoreResponseException("Couldn't parse Turtle from triplestore"))
          },
        ZIO.succeed(_)
      )
    }

    for {
      turtle   <- ZIO.scoped(execConstruct(request.sparql).flatMap(modelToTurtle)).orDie
      response <- parseTurtleResponse(turtle).orDie
    } yield response
  }

  private def execConstruct(query: String): ZIO[Any with Scope, Throwable, Model] = {
    def executeQuery(qExec: QueryExecution) = ZIO.attempt(qExec.execConstruct(ModelFactory.createDefaultModel()))
    def closeModel(model: Model)            = ZIO.succeed(model.close())
    getReadTransactionQueryExecution(query).flatMap(qExec => ZIO.acquireRelease(executeQuery(qExec))(closeModel))
  }

  private val byteArrayOutputStream: ZIO[Any with Scope, Throwable, ByteArrayOutputStream] = {
    def acquire                   = ZIO.attempt(new ByteArrayOutputStream())
    def release(os: OutputStream) = ZIO.succeed(os.close())
    ZIO.acquireRelease(acquire)(release)
  }

  private def fileInputStream(path: Path): ZIO[Any with Scope, Throwable, InputStream] = {
    def acquire = ZIO.attempt(Files.newInputStream(path))
    if (!Files.exists(path)) {
      ZIO.fail(new IllegalArgumentException(s"File ${path.toAbsolutePath} does not exist"))
    } else {
      ZIO.acquireRelease(acquire)(release).flatMap(bufferedInputStream)
    }
  }
  private def release(in: InputStream): UIO[Unit] =
    ZIO.attempt(in.close()).logError("Unable to close InputStream.").ignore
  private def bufferedInputStream(in: InputStream): ZIO[Any with Scope, Throwable, InputStream] = {
    def acquire = ZIO.attempt(new BufferedInputStream(in))
    ZIO.acquireRelease(acquire)(release)
  }

  private def modelToTurtle(model: Model): ZIO[Any with Scope, Throwable, String] =
    for {
      os    <- byteArrayOutputStream
      _      = model.write(os, Turtle.toString.toUpperCase)
      turtle = new String(os.toByteArray)
    } yield turtle

  override def sparqlHttpExtendedConstruct(
    request: SparqlExtendedConstructRequest
  ): UIO[SparqlExtendedConstructResponse] =
    for {
      turtle   <- ZIO.scoped(execConstruct(request.sparql).flatMap(modelToTurtle)).orDie
      response <- SparqlExtendedConstructResponse.parseTurtleResponse(turtle).orDie
    } yield response

  override def sparqlHttpConstructFile(
    sparql: String,
    graphIri: IRI,
    outputFile: Path,
    outputFormat: QuadFormat
  ): UIO[FileWrittenResponse] = ZIO.scoped {
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
    } yield FileWrittenResponse()
  }.orDie

  override def sparqlHttpUpdate(query: String): UIO[SparqlUpdateResponse] = {
    def doUpdate(ds: Dataset) = ZIO.attempt {
      val update    = UpdateFactory.create(query)
      val processor = UpdateExecutionFactory.create(update, ds)
      processor.execute()
    }
    ZIO.scoped {
      getDataSetWithTransaction(ReadWrite.WRITE).flatMap(doUpdate(_).as(SparqlUpdateResponse()))
    }.orDie
  }

  override def sparqlHttpGraphFile(
    graphIri: IRI,
    outputFile: Path,
    outputFormat: QuadFormat
  ): UIO[FileWrittenResponse] = ???

  override def sparqlHttpGraphData(graphIri: IRI): UIO[NamedGraphDataResponse] = ???

  override def resetTripleStoreContent(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean
  ): UIO[ResetRepositoryContentACK] = for {
    _ <- dropAllTriplestoreContent()
    _ <- insertDataIntoTriplestore(rdfDataObjects, prependDefaults)
  } yield ResetRepositoryContentACK()

  private val setEmptyDataSetRef: UIO[Unit] = TestDatasetBuilder.createEmptyDataset.flatMap(ds => datasetRef.set(ds))

  override def dropAllTriplestoreContent(): UIO[DropAllRepositoryContentACK] =
    setEmptyDataSetRef.as(DropAllRepositoryContentACK())

  override def dropDataGraphByGraph(): UIO[DropDataGraphByGraphACK] =
    dropAllTriplestoreContent().as(DropDataGraphByGraphACK())

  override def insertDataIntoTriplestore(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean
  ): UIO[InsertTriplestoreContentACK] =
    for {
      objects <- getListToInsert(rdfDataObjects, prependDefaults)
      _       <- ZIO.foreachDiscard(objects)(insert).orDie
    } yield InsertTriplestoreContentACK()

  private def getListToInsert(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean
  ): UIO[List[RdfDataObject]] = {
    val listToInsert = if (prependDefaults) { DefaultRdfData.data.toList ::: rdfDataObjects }
    else { rdfDataObjects }
    if (listToInsert.isEmpty) {
      ZIO.die(new IllegalArgumentException("List to insert is empty."))
    } else {
      ZIO.succeed(listToInsert)
    }
  }

  private def insert(elem: RdfDataObject): ZIO[Any, Throwable, Unit] = {
    val inputFile = Paths.get(elem.path)
    ZIO.scoped {
      for {
        graphName <- checkGraphName(elem)
        in        <- fileInputStream(inputFile)
        ds        <- getDataSetWithTransaction(ReadWrite.WRITE)
        model      = ds.getNamedModel(graphName)
        _          = model.read(in, null, "TTL")
      } yield ()
    }
  }

  private def checkGraphName(elem: RdfDataObject): Task[String] = {
    val graphName = elem.name
    if (graphName == "default") {
      ZIO.fail(new TriplestoreUnsupportedFeatureException("Requests to the default graph are not supported"))
    } else {
      ZIO.succeed(graphName)
    }
  }

  override def checkTriplestore(): UIO[CheckTriplestoreResponse] = ZIO.succeed(CheckTriplestoreResponse.Available)

  override def downloadRepository(outputFile: Path): UIO[FileWrittenResponse] =
    ZIO.die(new UnsupportedOperationException("Not implemented in fake"))

  override def uploadRepository(inputFile: Path): UIO[RepositoryUploadedResponse] =
    ZIO.die(new UnsupportedOperationException("Not implemented in fake"))

  override def insertDataGraphRequest(turtle: String, graphName: String): UIO[InsertGraphDataContentResponse] =
    ZIO.scoped {
      for {
        ds <- getDataSetWithTransaction(ReadWrite.WRITE)
        _   = ds.getNamedModel(graphName).read(new StringReader(turtle), null, "TTL")
      } yield InsertGraphDataContentResponse()
    }
}

object TriplestoreServiceFake {
  val layer: ZLayer[Ref[Dataset] with StringFormatter, Nothing, TriplestoreService] =
    ZLayer.fromFunction(TriplestoreServiceFake.apply _)
}
