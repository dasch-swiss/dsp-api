/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.impl

import org.apache.commons.lang3.StringUtils
import org.apache.http.Consts
import org.apache.http.HttpEntity
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.NameValuePair
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.utils.URIBuilder
import org.apache.http.config.SocketConfig
import org.apache.http.entity.ContentType
import org.apache.http.entity.FileEntity
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import spray.json.*
import zio.*
import zio.metrics.Metric

import java.io.BufferedInputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.temporal.ChronoUnit
import java.util

import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.config.Triplestore
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.SparqlResultProtocol.*
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlQuery
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.defaults.DefaultRdfData
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus.Available
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus.NotInitialized
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus.Unavailable
import org.knora.webapi.store.triplestore.errors.*
import org.knora.webapi.util.FileUtil

case class TriplestoreServiceLive(
  triplestoreConfig: Triplestore,
  queryHttpClient: CloseableHttpClient,
  targetHost: HttpHost,
  implicit val sf: StringFormatter
) extends TriplestoreService
    with FusekiTriplestore {

  private val requestTimer =
    Metric.timer(
      "fuseki_request_duration",
      ChronoUnit.MILLIS,
      // 7 buckets for upper bounds 10ms, 100ms, 1s, 10s, 1.6m, 16.6m, inf
      Chunk.iterate(10.0, 6)(_ * 10)
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
    outputFormat: QuadFormat
  ): Task[Unit] =
    executeSparqlQuery(query, acceptMimeType = mimeTypeTextTurtle)
      .map(RdfStringSource)
      .mapAttempt(RdfFormatUtil.turtleToQuadsFile(_, graphIri.value, outputFile.toFile.toPath, outputFormat))

  /**
   * Performs a SPARQL update operation.
   *
   * @param query the SPARQL [[Update]] query.
   * @return [[Unit]].
   */
  override def query(query: Update): Task[Unit] = {
    val request = new HttpPost(paths.update)
    request.setEntity(new StringEntity(query.sparql, ContentType.create(mimeTypeApplicationSparqlUpdate, Consts.UTF_8)))
    trackQueryDuration(query, doHttpRequest(request, _ => ZIO.unit))
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
    prependDefaults: Boolean
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

  private def dropGraph(graphName: String): Task[Unit] =
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
      res     <- query(Select("select ?g {graph ?g {?s ?p ?o}} group by ?g", isGravsearch = false))
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
    prependDefaults: Boolean
  ): Task[Unit] = {

    val calculateCompleteRdfDataObjectList: Task[NonEmptyChunk[RdfDataObject]] =
      if (prependDefaults) { // prepend
        if (rdfDataObjects.isEmpty) {
          ZIO.succeed(DefaultRdfData.data)
        } else {
          // prepend default data objects like those of knora-base, knora-admin, etc.
          ZIO.succeed(DefaultRdfData.data ++ NonEmptyChunk.fromIterable(rdfDataObjects.head, rdfDataObjects.tail))
        }
      } else { // don't prepend
        if (rdfDataObjects.isEmpty) {
          ZIO.fail(BadRequestException("Cannot insert list with empty data into triplestore."))
        } else {
          ZIO.succeed(NonEmptyChunk.fromIterable(rdfDataObjects.head, rdfDataObjects.tail))
        }
      }

    for {
      _    <- ZIO.logDebug("==>> Loading Data Start")
      list <- calculateCompleteRdfDataObjectList
      request <-
        ZIO.foreach(list)(elem =>
          for {
            graphName <-
              if (elem.name.toLowerCase == "default") {
                ZIO.fail(TriplestoreUnsupportedFeatureException("Requests to the default graph are not supported"))
              } else {
                ZIO.succeed(elem.name)
              }

            uriBuilder <-
              ZIO.attempt {
                val uriBuilder: URIBuilder = new URIBuilder(paths.data)
                uriBuilder.addParameter("graph", graphName) // Note: addParameter encodes the graphName URL
                uriBuilder
              }

            httpPost <-
              ZIO.attemptBlocking {
                val httpPost = new HttpPost(uriBuilder.build())
                // Add the input file to the body of the request.
                // here we need to tweak the base directory path from "webapi"
                // to the parent folder where the files can be found
                val inputFile = Paths.get("..", elem.path)
                if (!Files.exists(inputFile)) {
                  throw BadRequestException(s"File ${inputFile.toAbsolutePath} does not exist")
                }
                val fileEntity =
                  new FileEntity(inputFile.toFile, ContentType.create(mimeTypeTextTurtle, "UTF-8"))
                httpPost.setEntity(fileEntity)
                httpPost
              }
            responseHandler <- ZIO.attempt(returnInsertGraphDataResponse(graphName)(_))
          } yield (httpPost, responseHandler)
        )
      _ <- ZIO.foreachDiscard(request)(elem => doHttpRequest(request = elem._1, processResponse = elem._2))
      _ <- ZIO.logDebug("==>> Loading Data End")
    } yield ()
  }

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
              ZIO.succeed(notInitialized)
            )
        } else {
          ZIO.succeed(notInitialized)
        }
      )
      .catchAll(ex => ZIO.succeed(unavailable(ex.getMessage)))
  }

  /**
   * Call an endpoint that returns all datasets and check if our required dataset is present.
   */
  private def checkTriplestoreInitialized(): Task[Boolean] = {

    val request = new HttpGet(paths.checkServer)
    request.addHeader("Accept", mimeTypeApplicationJson)

    def checkForExpectedDataset(response: String) = ZIO.attempt {
      val nameShouldBe = fusekiConfig.repositoryName
      import org.knora.webapi.messages.store.triplestoremessages.FusekiJsonProtocol.*
      val fusekiServer: FusekiServer = JsonParser(response).convertTo[FusekiServer]
      val neededDataset: Option[FusekiDataset] =
        fusekiServer.datasets.find(dataset => dataset.dsName == s"/$nameShouldBe" && dataset.dsState)
      neededDataset.nonEmpty
    }

    doHttpRequest(request, returnResponseAsString).flatMap(checkForExpectedDataset)
  }

  /**
   * Initialize the Jena Fuseki triplestore. Currently only works for
   * 'knora-test' and 'knora-test-unit' repository names. To be used, the
   * API needs to be started with 'KNORA_WEBAPI_TRIPLESTORE_AUTOINIT' set
   * to 'true' (appConfig.triplestore.autoInit). This is set to `true` for tests
   * (`test/resources/test.conf`). Usage is only recommended for automated
   * testing and not for production use.
   */
  private def initJenaFusekiTriplestore(): Task[Unit] = {

    val httpPost = ZIO.attemptBlocking {
      val configFileName = s"fuseki-repository-config.ttl.template"

      // take config from the classpath and write to triplestore
      val configFile: String =
        FileUtil.readTextResource(configFileName).replace("@REPOSITORY@", fusekiConfig.repositoryName)

      val httpPost: HttpPost = new HttpPost("/$/datasets")
      val stringEntity       = new StringEntity(configFile, ContentType.create(mimeTypeTextTurtle))
      httpPost.setEntity(stringEntity)
      httpPost
    }

    httpPost.flatMap(doHttpRequest(_, _ => ZIO.unit)).unit
  }

  /**
   * Makes a triplestore URI for downloading a named graph.
   *
   * @param graphIri the IRI of the named graph.
   * @return a triplestore-specific URI for downloading the named graph.
   */
  private def makeNamedGraphDownloadUri(graphIri: IRI): URI = {
    val uriBuilder: URIBuilder = new URIBuilder(paths.get)
    uriBuilder.setParameter("graph", s"$graphIri")
    uriBuilder.build()
  }

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
    outputFormat: QuadFormat
  ): Task[Unit] = {
    val request = new HttpGet(makeNamedGraphDownloadUri(graphIri.value))
    request.addHeader("Accept", mimeTypeTextTurtle)
    doHttpRequest(request, writeResponseFileAsTurtleContent(outputFile.toFile.toPath, graphIri.value, outputFormat))
  }.unit

  private def executeSparqlQuery(
    query: SparqlQuery,
    acceptMimeType: String = mimeTypeApplicationSparqlResultsJson
  ) = {
    // in case of a gravsearch query, a longer timeout is set
    val timeout =
      if (query.isGravsearch) triplestoreConfig.gravsearchTimeout.toSeconds.toInt.toString
      else triplestoreConfig.queryTimeout.toSeconds.toInt.toString

    val formParams = new util.ArrayList[NameValuePair]()
    formParams.add(new BasicNameValuePair("query", query.sparql))
    formParams.add(new BasicNameValuePair("timeout", timeout))

    val request: HttpPost = new HttpPost(paths.query)
    request.setEntity(new UrlEncodedFormEntity(formParams, Consts.UTF_8))
    request.addHeader("Accept", acceptMimeType)
    trackQueryDuration(query, doHttpRequest(request, returnResponseAsString))
  }

  private def trackQueryDuration[T](query: SparqlQuery, reqTask: Task[T]): Task[T] = {
    val trackingThreshold = fusekiConfig.queryLoggingThreshold
    val startTime         = java.lang.System.nanoTime()
    for {
      result <- reqTask @@ requestTimer
                  .tagged("type", query.getClass.getSimpleName)
                  .tagged("isGravsearch", query.isGravsearch.toString)
                  .trackDuration
      _ <- {
             val endTime  = java.lang.System.nanoTime()
             val duration = Duration.fromNanos(endTime - startTime)
             ZIO.when(duration >= trackingThreshold) {
               ZIO.logInfo(
                 s"Fuseki request took $duration, which is longer than $trackingThreshold, isGravSearch=${query.isGravsearch}\n ${query.sparql}"
               )
             }
           }.ignore
    } yield result
  }

  /**
   * Dumps the whole repository in N-Quads format, saving the response in a file.
   *
   * @param outputFile           the output file.
   * @return a string containing the contents of the graph in N-Quads format.
   */
  override def downloadRepository(outputFile: Path): Task[Unit] = {
    val request = new HttpGet(paths.repository)
    request.addHeader("Accept", mimeTypeApplicationNQuads)
    doHttpRequest(request, writeResponseFileAsPlainContent(outputFile)).unit
  }

  /**
   * Uploads repository content from an N-Quads file.
   *
   * @param inputFile an N-Quads file containing the content to be uploaded to the repository.
   */
  override def uploadRepository(inputFile: Path): Task[Unit] = {
    val fileEntity        = new FileEntity(inputFile.toFile, ContentType.create(mimeTypeApplicationNQuads, "UTF-8"))
    val request: HttpPost = new HttpPost(paths.repository)
    request.setEntity(fileEntity)
    doHttpRequest(request, _ => ZIO.unit).unit
  }

  /**
   * Makes an HTTP connection to the triplestore, and delegates processing of the response
   * to a function.
   *
   * @param request         the request to be sent.
   * @tparam T the return type of `processError`.
   * @return the return value of `processError`.
   */
  private def doHttpRequest[T](
    request: HttpRequest,
    processResponse: CloseableHttpResponse => Task[T]
  ): Task[T] = {

    def executeQuery(): Task[CloseableHttpResponse] = {
      ZIO
        .attempt(queryHttpClient.execute(targetHost, request))
        .catchSome {
          case socketTimeoutException: java.net.SocketTimeoutException =>
            val message =
              "The triplestore took too long to process a request. This can happen because the triplestore needed too much time to search through the data that is currently in the triplestore. Query optimisation may help."
            val error = TriplestoreTimeoutException(message, socketTimeoutException)
            ZIO.logError(error.toString) *>
              ZIO.fail(error)
          case e: Exception =>
            val message = s"Failed to connect to triplestore."
            val error   = TriplestoreConnectionException(message, Some(e))
            ZIO.logError(error.toString) *>
              ZIO.fail(error)
        }
    } <* ZIO.logDebug(s"Executing Query: $request")

    def checkResponse(response: CloseableHttpResponse, statusCode: Int): Task[Unit] =
      ZIO
        .unless(statusCode / 100 == 2) {
          val entity =
            Option(response.getEntity)
              .map(responseEntity => EntityUtils.toString(responseEntity, StandardCharsets.UTF_8))

          val statusResponseMsg =
            s"Triplestore responded with HTTP code $statusCode"

          (statusCode, entity) match {
            case (404, _) => ZIO.fail(NotFoundException.notFound)
            case (400, Some(response)) if response.contains("Text search parse error") =>
              ZIO.fail(BadRequestException(s"$response"))
            case (500, _) => ZIO.fail(TriplestoreResponseException(statusResponseMsg))
            case (503, Some(response)) if response.contains("Query timed out") =>
              ZIO.fail(TriplestoreTimeoutException(s"$statusResponseMsg: $response"))
            case (503, _) => ZIO.fail(TriplestoreResponseException(statusResponseMsg))
            case _        => ZIO.fail(TriplestoreResponseException(statusResponseMsg))
          }
        }
        .unit

    def getResponse = ZIO.acquireRelease(executeQuery())(response => ZIO.succeed(response.close()))

    ZIO.scoped(for {
      _          <- ZIO.logDebug("Executing query...")
      response   <- getResponse
      statusCode <- ZIO.attempt(response.getStatusLine.getStatusCode)
      _          <- ZIO.logDebug(s"Executing query done with status code: $statusCode")
      _          <- checkResponse(response, statusCode)
      _          <- ZIO.logDebug("Checking response done.")
      result     <- processResponse(response)
      _          <- ZIO.logDebug("Processing response done.")
    } yield result)
  }

  /**
   * Attempts to transforms a [[CloseableHttpResponse]] to a [[String]].
   */
  private def returnResponseAsString(response: CloseableHttpResponse): Task[String] =
    Option(response.getEntity) match {
      case None => ZIO.succeed("")
      case Some(responseEntity) =>
        ZIO
          .attempt(EntityUtils.toString(responseEntity, StandardCharsets.UTF_8))
          .tapDefect(e => ZIO.logError(s"Failed to return response as string: $e"))
    }

  /**
   * Attempts to transforms a [[CloseableHttpResponse]] to a [[Unit]].
   */
  private def returnInsertGraphDataResponse(
    graphName: String
  )(response: CloseableHttpResponse): Task[Unit] =
    Option(response.getEntity) match {
      case None    => ZIO.fail(TriplestoreResponseException(s"$graphName could not be inserted into Triplestore."))
      case Some(_) => ZIO.unit
    }

  /**
   * Writes an HTTP response the response is written as-is to the output file.
   *
   * @param outputFile             the output file.
   * @param response               the response to be read.
   * @return a [[Unit]].
   */
  private def writeResponseFileAsPlainContent(
    outputFile: Path
  )(response: CloseableHttpResponse): Task[Unit] =
    Option(response.getEntity) match {
      case Some(responseEntity: HttpEntity) =>
        // Stream the HTTP entity directly to the output file.
        ZIO.attempt(Files.copy(responseEntity.getContent, outputFile)).unit
      case None =>
        val error = TriplestoreResponseException(s"Triplestore returned no content for for repository dump")
        ZIO.logError(error.toString) *>
          ZIO.fail(error)
    }

  /**
   * Writes an HTTP response to a file, where the response is parsed as Turtle
   * and converted to the output format, with the graph IRI added to each statement.
   *
   * @param outputFile        the output file.
   * @param graphIri           the IRI of the graph used in the output.
   * @param quadFormat         the output format.
   * @param response           the response to be read.
   * @return a [[Unit]].
   */
  private def writeResponseFileAsTurtleContent(
    outputFile: Path,
    graphIri: IRI,
    quadFormat: QuadFormat
  )(response: CloseableHttpResponse): Task[Unit] =
    Option(response.getEntity) match {
      case Some(responseEntity: HttpEntity) =>
        ZIO.attemptBlocking {
          // Yes. Stream the HTTP entity to a temporary Turtle file.
          val tempTurtleFile = Paths.get(outputFile.toString + ".ttl")
          Files.copy(responseEntity.getContent, tempTurtleFile, StandardCopyOption.REPLACE_EXISTING)

          RdfFormatUtil.turtleToQuadsFile(
            RdfInputStreamSource(new BufferedInputStream(Files.newInputStream(tempTurtleFile))),
            graphIri,
            outputFile,
            quadFormat
          )

          Files.delete(tempTurtleFile)
          ()
        }

      case None =>
        val message = s"Triplestore returned no content for graph $graphIri"
        val error   = TriplestoreResponseException(message)
        ZIO.logError(error.toString) *>
          ZIO.fail(error)
    }
}

object TriplestoreServiceLive {
  private def makeHttpClient(config: Triplestore, host: HttpHost) =
    ZIO.acquireRelease {
      val connManager = new PoolingHttpClientConnectionManager()
      connManager.setDefaultSocketConfig(SocketConfig.custom().setTcpNoDelay(true).build())
      connManager.setValidateAfterInactivity(1000)
      connManager.setMaxTotal(100)
      connManager.setDefaultMaxPerRoute(15)

      val credentialsProvider = new BasicCredentialsProvider
      credentialsProvider.setCredentials(
        new AuthScope(host.getHostName, host.getPort),
        new UsernamePasswordCredentials(config.fuseki.username, config.fuseki.password)
      )

      // the client config used for queries to the triplestore. The timeout has to be larger than
      // tripleStoreConfig.queryTimeoutAsDuration and tripleStoreConfig.gravsearchTimeoutAsDuration.
      val requestTimeoutMillis = 7_200_000 // 2 hours
      val requestConfig = RequestConfig
        .custom()
        .setConnectTimeout(requestTimeoutMillis)
        .setConnectionRequestTimeout(requestTimeoutMillis)
        .setSocketTimeout(requestTimeoutMillis)
        .build

      val httpClient: CloseableHttpClient = HttpClients
        .custom()
        .setConnectionManager(connManager)
        .setDefaultCredentialsProvider(credentialsProvider)
        .setDefaultRequestConfig(requestConfig)
        .build()
      ZIO.succeed(httpClient)
    }(client => ZIO.attemptBlocking(client.close()).logError.ignore)

  val layer: URLayer[Triplestore & StringFormatter, TriplestoreService] =
    ZLayer.scoped {
      for {
        sf     <- ZIO.service[StringFormatter]
        config <- ZIO.service[Triplestore]
        host    = new HttpHost(config.host, config.fuseki.port, "http")
        client <- makeHttpClient(config, host)
      } yield TriplestoreServiceLive(config, client, host, sf)
    }
}
