/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.impl

import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpHost
import spray.json.*
import sttp.capabilities.zio.ZioStreams
import sttp.client3.Empty
import sttp.client3.Request
import sttp.client3.RequestT
import sttp.client3.Response
import sttp.client3.SttpBackend
import sttp.client3.SttpBackendOptions
import sttp.client3.UriContext
import sttp.client3.basicRequest
import sttp.client3.httpclient.zio.HttpClientZioBackend
import zio.*
import zio.metrics.Metric
import zio.nio.file.Path as NioPath

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption.*
import java.time.temporal.ChronoUnit
import scala.io.Source

import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.config.Triplestore
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.store.triplestoremessages.FusekiJsonProtocol.fusekiServerFormat
import org.knora.webapi.messages.store.triplestoremessages.SparqlResultProtocol.*
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.*
import org.knora.webapi.store.triplestore.defaults.DefaultRdfData
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus.Available
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus.NotInitialized
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus.Unavailable
import org.knora.webapi.store.triplestore.errors.*
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration
import org.knora.webapi.store.triplestore.upgrade.MigrateAllGraphs
import org.knora.webapi.store.triplestore.upgrade.MigrateSpecificGraphs
import org.knora.webapi.util.FileUtil

case class TriplestoreServiceLive(
  triplestoreConfig: Triplestore,
  sttp: SttpBackend[Task, ZioStreams],
)(implicit val sf: StringFormatter)
    extends TriplestoreService
    with FusekiTriplestore {

  private val targetHost = new HttpHost(triplestoreConfig.host, triplestoreConfig.fuseki.port, "http")
  private val targetHostUri =
    uri"${(targetHost).toURI}"

  // NOTE: possibly quickRequest might be used instead of basicRequest (no Either)
  private val authenticatedRequest: RequestT[Empty, Either[String, String], Any] =
    basicRequest.auth.basic(triplestoreConfig.fuseki.username, triplestoreConfig.fuseki.password)

  private val requestTimer =
    Metric.timer(
      "fuseki_request_duration",
      ChronoUnit.MILLIS,
      // 7 buckets for upper bounds 10ms, 100ms, 1s, 10s, 1.6m, 16.6m, inf
      Chunk.iterate(10.0, 6)(_ * 10),
    )

  private def processError(sparql: String, response: String): IO[TriplestoreException, Nothing] =
    if (response.contains("##  Query cancelled due to timeout during execution")) {
      val msg: String = "Triplestore timed out while sending a response, after sending statuscode 200."
      ZIO.logError(msg) *> ZIO.fail(TriplestoreTimeoutException(msg))
    } else {
      val delimiter: String = "\n" + StringUtils.repeat('=', 80) + "\n"
      val msg =
        s"Couldn't parse response from triplestore:$delimiter$response${delimiter}in response to SPARQL query:$delimiter$sparql"
      ZIO.logError(msg) *> ZIO.fail(TriplestoreResponseException("Couldn't parse Turtle from triplestore"))
    }

  /**
   * Given a SPARQL SELECT query string, runs the query, returning the result as a [[SparqlSelectResult]].
   *
   * @param query          the SPARQL SELECT query string.
   * @return a [[SparqlSelectResult]].
   */
  override def query(query: Select): Task[SparqlSelectResult] = {
    def parseJsonResponse(sparql: String, resultStr: String): IO[TriplestoreException, SparqlSelectResult] =
      ZIO
        .attemptBlocking(resultStr.parseJson.convertTo[SparqlSelectResult])
        .orElse(processError(sparql, resultStr))

    executeSparqlQuery(query).flatMap(parseJsonResponse(query.sparql, _))
  }

  /**
   * Given a SPARQL CONSTRUCT query string, runs the query, returning the result as a [[SparqlConstructResponse]].
   *
   * @param query The SPARQL [[Construct]] query.
   * @return a [[SparqlConstructResponse]]
   */
  override def query(query: Construct): Task[SparqlConstructResponse] =
    for {
      turtleStr <- executeSparqlQuery(query, mimeTypeTextTurtle)
      rdfModel <- ZIO
                    .attempt(RdfFormatUtil.parseToRdfModel(turtleStr, Turtle))
                    .orElse(processError(query.sparql, turtleStr))
    } yield SparqlConstructResponse.make(rdfModel)

  override def queryRdf(sparql: Construct): Task[String] = executeSparqlQuery(sparql, mimeTypeTextTurtle)

  /**
   * Given a SPARQL CONSTRUCT query string, runs the query, saving the result in a file.
   *
   * @param query       the SPARQL CONSTRUCT query string.
   * @param graphIri     the named graph IRI to be used in the output file.
   * @param outputFile   the output file.
   * @param outputFormat the output file format.
   * @return a [[Unit]].
   */
  override def queryToFile(
    query: Construct,
    graphIri: InternalIri,
    outputFile: zio.nio.file.Path,
    outputFormat: QuadFormat,
  ): Task[Unit] =
    executeSparqlQuery(query, acceptMimeType = mimeTypeTextTurtle)
      .map(RdfStringSource.apply)
      .mapAttempt(RdfFormatUtil.turtleToQuadsFile(_, graphIri.value, outputFile.toFile.toPath, outputFormat))

  /**
   * Performs a SPARQL update operation.
   *
   * @param query the SPARQL [[Update]] query.
   * @return [[Unit]].
   */
  override def query(query: Update): Task[Unit] = {
    val request = authenticatedRequest
      .post(targetHostUri.addPath(paths.update))
      .body(query.sparql)
      .contentType(mimeTypeApplicationSparqlUpdate)

    trackQueryDuration(query, doHttpRequest(request)).unit
  }

  /**
   * Performs a SPARQL ASK query.
   *
   * @param query the SPARQL [[Ask]] query.
   * @return a [[Boolean]].
   */
  override def query(query: Ask): Task[Boolean] =
    for {
      resultString <- executeSparqlQuery(query)
      _            <- ZIO.logDebug(s"sparqlHttpAsk - resultString: $resultString")
      result       <- ZIO.attemptBlocking(resultString.parseJson.asJsObject.getFields("boolean").head.convertTo[Boolean])
    } yield result

  /**
   * Resets the content of the triplestore with the data supplied with the request.
   * First performs `dropAllTriplestoreContent` and afterwards `insertDataIntoTriplestore`.
   *
   * @param rdfDataObjects a sequence of paths and graph names referencing data that needs to be inserted.
   * @param prependDefaults denotes if the rdfDataObjects list should be prepended with a default set. Default is `true`.
   */
  def resetTripleStoreContent(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean,
  ): Task[Unit] =
    for {
      _ <- ZIO.logDebug("resetTripleStoreContent")
      _ <- dropDataGraphByGraph()
      _ <- insertDataIntoTriplestore(rdfDataObjects, prependDefaults)
    } yield ()

  /**
   * Drops all triplestore data graph by graph using "DROP GRAPH" SPARQL query.
   */
  def dropDataGraphByGraph(): Task[Unit] =
    for {
      _      <- ZIO.logInfo("==>> Drop All Data Start")
      graphs <- getAllGraphs
      _      <- ZIO.logInfo(s"Number of graphs found: ${graphs.length}")
      _      <- ZIO.foreachDiscard(graphs)(dropGraph)
      _      <- ZIO.logInfo("==>> Drop All Data End")
    } yield ()

  override def dropGraph(graphName: String): Task[Unit] =
    ZIO.logInfo(s"==>> Dropping graph: $graphName") *>
      query(Update(s"DROP GRAPH <$graphName>")) *>
      ZIO.logDebug("Graph dropped")

  /**
   * Gets all graphs stored in the triplestore.
   *
   * @return All graphs stored in the triplestore as a [[Seq[String]]
   */
  private def getAllGraphs: Task[Seq[String]] =
    for {
      res     <- query(Select("select ?g {graph ?g {?s ?p ?o}} group by ?g"))
      bindings = res.results.bindings
      graphs   = bindings.map(_.rowMap("g"))
    } yield graphs

  /**
   * Inserts the data referenced inside the `rdfDataObjects` by appending it to a default set of `rdfDataObjects`
   * based on the list defined in `application.conf` under the `app.triplestore.default-rdf-data` key.
   *
   * @param rdfDataObjects  a sequence of paths and graph names referencing data that needs to be inserted.
   * @param prependDefaults denotes if the rdfDataObjects list should be prepended with a default set. Default is `true`.
   * @return [[Unit]]
   */
  override def insertDataIntoTriplestore(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean,
  ): Task[Unit] =
    for {
      _      <- ZIO.logDebug("==>> Loading Data Start")
      objects = DefaultRdfData.data.toList.filter(_ => prependDefaults) ++ rdfDataObjects
      _      <- ZIO.foreach(objects)(insertObjectIntoTriplestore)
      _      <- ZIO.logDebug("==>> Loading Data End")
    } yield ()

  /**
   * Insert the RdfDataObject into the triplestore from either a relative ../$path or through a resource.
   */
  private def insertObjectIntoTriplestore(rdfDataObject: RdfDataObject): Task[Unit] =
    for {
      graphName <- ZIO
                     .fail(TriplestoreUnsupportedFeatureException("Requests to the default graph are not supported"))
                     .when(rdfDataObject.name.toLowerCase == "default")
                     .as(rdfDataObject.name)
      rdfContents <- ZIO
                       .readFile(Paths.get("..", rdfDataObject.path))
                       .orElse(ZIO.attemptBlocking(Source.fromResource(rdfDataObject.path).mkString))
      request = authenticatedRequest
                  .post(targetHostUri.addPath(paths.data).addParam("graph", graphName))
                  .body(rdfContents)
                  .contentType(mimeTypeTextTurtle)
      _ <- ZIO.logDebug(s"INSERT: ${request.uri}")
      _ <- doHttpRequest(request).map(_.body).flatMap(ensuringBody(_))
    } yield ()

  /**
   * Checks the Fuseki triplestore if it is available and configured correctly. If it is not
   * configured, tries to automatically configure (initialize) the required dataset.
   */
  def checkTriplestore(): Task[TriplestoreStatus] = {
    val notInitialized =
      NotInitialized(s"None of the active datasets meet our requirement of name: ${fusekiConfig.repositoryName}")

    def unavailable(cause: String) =
      Unavailable(s"Triplestore not available: $cause")

    ZIO
      .ifZIO(checkTriplestoreInitialized())(
        ZIO.succeed(Available),
        if (triplestoreConfig.autoInit) {
          ZIO
            .ifZIO(initJenaFusekiTriplestore() *> checkTriplestoreInitialized())(
              ZIO.succeed(Available),
              ZIO.succeed(notInitialized),
            )
        } else {
          ZIO.succeed(notInitialized)
        },
      )
      .catchAll(ex => ZIO.succeed(unavailable(ex.getMessage)))
  }

  /**
   * Call an endpoint that returns all datasets and check if our required dataset is present.
   */
  private def checkTriplestoreInitialized(): Task[Boolean] =
    for {
      response <- doHttpRequest(
                    authenticatedRequest
                      .get(targetHostUri.addPath(paths.checkServer))
                      .header("Accept", mimeTypeApplicationJson),
                  )
      expectedFound <- ZIO.attempt {
                         JsonParser(response.body.toOption.getOrElse(""))
                           .convertTo[FusekiServer]
                           .datasets
                           .find(dataset => dataset.dsName == s"/${fusekiConfig.repositoryName}" && dataset.dsState)
                           .nonEmpty
                       }
    } yield expectedFound

  /**
   * Initialize the Jena Fuseki triplestore. Currently only works for
   * 'knora-test' and 'knora-test-unit' repository names. To be used, the
   * API needs to be started with 'KNORA_WEBAPI_TRIPLESTORE_AUTOINIT' set
   * to 'true' (appConfig.triplestore.autoInit). This is set to `true` for tests
   * (`test/resources/test.conf`). Usage is only recommended for automated
   * testing and not for production use.
   */
  private def initJenaFusekiTriplestore(): Task[Unit] =
    for {
      configFile <-
        ZIO.attemptBlocking {
          FileUtil
            .readTextResource(s"fuseki-repository-config.ttl.template")
            .replace("@REPOSITORY@", fusekiConfig.repositoryName)
        }

      _ <-
        doHttpRequest(
          authenticatedRequest
            .post(targetHostUri.addPath(paths.datasets))
            .contentType(mimeTypeTextTurtle)
            .body(configFile),
        )
    } yield ()

  /**
   * Requests the contents of a named graph, saving the response in a file.
   *
   * @param graphIri             the IRI of the named graph.
   * @param outputFile           the file to be written.
   * @param outputFormat         the output file format.
   * @return a string containing the contents of the graph in N-Quads format.
   */
  override def downloadGraph(
    graphIri: InternalIri,
    outputFile: zio.nio.file.Path,
    outputFormat: QuadFormat,
  ): Task[Unit] =
    for {
      request <-
        ZIO.succeed(
          authenticatedRequest
            .get(targetHostUri.addPath(paths.get).addParam("graph", s"${graphIri.value}"))
            .header("Accept", mimeTypeTextTurtle),
        )
      response <- doHttpRequest(request)
      rdfBody  <- ensuringBody(response.body).map(RdfStringSource(_))
      _ <- ZIO.attemptBlocking {
             RdfFormatUtil.turtleToQuadsFile(rdfBody, graphIri.value, outputFile.toFile.toPath, outputFormat, APPEND)
           }
    } yield ()

  private def executeSparqlQuery(
    query: SparqlQuery,
    acceptMimeType: String = mimeTypeApplicationSparqlResultsJson,
  ): Task[String] = {
    val timeout: Duration = query.timeout match {
      case SparqlTimeout.Standard    => triplestoreConfig.queryTimeout
      case SparqlTimeout.Maintenance => triplestoreConfig.maintenanceTimeout
      case SparqlTimeout.Gravsearch  => triplestoreConfig.gravsearchTimeout
    }

    val params  = Map(("query", query.sparql), ("timeout", timeout.toSeconds.toString))
    val uri     = targetHostUri.addPath(paths.query).addParams(params)
    val request = authenticatedRequest.post(uri).header("Accept", acceptMimeType)
    trackQueryDuration(query, doHttpRequest(request).map(_.body.merge))
  }

  private def trackQueryDuration[T](query: SparqlQuery, reqTask: Task[T]): Task[T] = {
    val trackingThreshold = fusekiConfig.queryLoggingThreshold
    val startTime         = java.lang.System.nanoTime()
    for {
      result <- reqTask @@ requestTimer
                  .tagged("type", query.getClass.getSimpleName)
                  .tagged("isGravsearch", s"${query == SparqlTimeout.Gravsearch}")
                  .tagged("isMaintenance", s"${query == SparqlTimeout.Maintenance}")
                  .trackDuration
      _ <- {
             val endTime  = java.lang.System.nanoTime()
             val duration = Duration.fromNanos(endTime - startTime)
             ZIO.when(duration >= trackingThreshold) {
               ZIO.logInfo(
                 s"Fuseki request took $duration, which is longer than $trackingThreshold, timeout=${query.timeout}\n ${query.sparql}",
               )
             }
           }.ignore
    } yield result
  }

  /**
   * Dumps the whole repository or only specific graphs in N-Quads format, saving the response in a file.
   *
   * @param outputFile  The path to the output file.
   * @param graphs      Specify which graphs are to be dumped.
   * @return [[Unit]]   Or fails if the export was not successful.
   */
  override def downloadRepository(outputFile: Path, graphs: GraphsForMigration): Task[Unit] =
    graphs match {
      case MigrateAllGraphs =>
        for {
          response <-
            doHttpRequest(
              authenticatedRequest
                .get(targetHostUri.addPath(paths.repository))
                .header("Accept", mimeTypeApplicationNQuads),
            )
          body <- ensuringBody(response.body)
          _    <- ZIO.attempt(Files.write(outputFile, body.getBytes(StandardCharsets.UTF_8), CREATE, TRUNCATE_EXISTING))
        } yield ()
      case MigrateSpecificGraphs(graphIris) =>
        ZIO.foreach(graphIris)(downloadGraph(_, NioPath.fromJava(outputFile), NQuads)).unit
    }

  private def ensuringBody(body: Either[String, String]): Task[String] =
    ZIO
      .succeed(body.toOption)
      .someOrFail(TriplestoreResponseException("Triplestore returned no content for for repository dump"))

  /**
   * Uploads repository content from an N-Quads file.
   *
   * @param inputFile an N-Quads file containing the content to be uploaded to the repository.
   */
  override def uploadRepository(inputFile: Path): Task[Unit] =
    ZIO
      .readFile(inputFile)
      .map(
        authenticatedRequest
          .post(targetHostUri.addPath(paths.repository))
          .contentType(mimeTypeApplicationNQuads, "UTF-8")
          .body(_),
      )
      .flatMap(doHttpRequest)
      .unit

  private def doHttpRequest[T](
    request: Request[Either[String, String], Any],
  ): Task[Response[Either[String, String]]] = {
    def executeQuery(request: Request[Either[String, String], Any]): Task[Response[Either[String, String]]] = {
      request.send(sttp).catchSome {
        case socketTimeoutException: java.net.SocketTimeoutException =>
          val message =
            "The triplestore took too long to process a request. This can happen because the triplestore needed too much time to search through the data that is currently in the triplestore. Query optimisation may help."
          val error = TriplestoreTimeoutException(message, socketTimeoutException)
          ZIO.logError(error.toString) *> ZIO.fail(error)
        case e: Exception =>
          val message = s"Failed to connect to triplestore."
          val error   = TriplestoreConnectionException(message, Some(e))
          ZIO.logError(error.toString) *> ZIO.fail(error)
      }
    } <* ZIO.logDebug(s"Executing Query: $request")

    def checkResponse(response: Response[Either[String, String]]): Task[Unit] =
      ZIO
        .unless(response.code.isSuccess) {
          val statusResponseMsg = s"Triplestore responded with HTTP code ${response.code}"

          (response.code.code, response.body.toOption) match {
            case (404, _) =>
              ZIO.fail(NotFoundException.notFound)
            case (400, Some(response)) if response.contains("Text search parse error") =>
              ZIO.fail(BadRequestException(s"$response"))
            case (503, Some(response)) if response.contains("Query timed out") =>
              ZIO.fail(TriplestoreTimeoutException(s"$statusResponseMsg: $response"))
            case _ =>
              ZIO.fail(TriplestoreResponseException(statusResponseMsg))
          }
        }
        .unit

    for {
      _        <- ZIO.logDebug("Executing query...")
      response <- executeQuery(request)
      _        <- ZIO.logDebug(s"Executed query with status code: ${response.code}")
      _        <- checkResponse(response)
    } yield response
  }
}

object TriplestoreServiceLive {
  import scala.concurrent.duration._

  val layer: URLayer[Triplestore & StringFormatter, TriplestoreService] =
    HttpClientZioBackend
      .layer(
        SttpBackendOptions.connectionTimeout(2.hours),
      )
      .orDie >+>
      ZLayer.scoped {
        for {
          sf     <- ZIO.service[StringFormatter]
          config <- ZIO.service[Triplestore]
          sttp   <- ZIO.service[SttpBackend[Task, ZioStreams]]
        } yield TriplestoreServiceLive(config, sttp)(sf)
      }
}
