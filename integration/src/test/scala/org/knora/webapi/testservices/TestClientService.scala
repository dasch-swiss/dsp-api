/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import org.apache.http
import org.apache.http.HttpHost
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.SocketConfig
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.util.EntityUtils
import org.apache.pekko
import org.apache.pekko.actor.ActorSystem
import spray.json.JsObject
import spray.json._
import zio._

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.store.sipimessages.SipiUploadResponse
import org.knora.webapi.messages.store.sipimessages.SipiUploadResponseJsonProtocol._
import org.knora.webapi.messages.store.sipimessages.SipiUploadWithoutProcessingResponse
import org.knora.webapi.messages.store.sipimessages.SipiUploadWithoutProcessingResponseJsonProtocol._
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.store.iiif.errors.SipiException
import org.knora.webapi.util.SipiUtil

import pekko.http.scaladsl.client.RequestBuilding
import pekko.http.scaladsl.unmarshalling.Unmarshal

/**
 * Represents a file to be uploaded to the IIF Service.
 *
 * @param path     the path of the file.
 * @param mimeType the MIME type of the file.
 */
final case class FileToUpload(path: Path, mimeType: ContentType)

/**
 * Represents an image file to be uploaded to the IIF Service.
 *
 * @param fileToUpload the file to be uploaded.
 * @param width        the image's width in pixels.
 * @param height       the image's height in pixels.
 */
final case class InputFile(fileToUpload: FileToUpload, width: Int, height: Int)

final case class TestClientService(config: AppConfig, httpClient: CloseableHttpClient)(implicit system: ActorSystem)
    extends TriplestoreJsonProtocol
    with RequestBuilding {

  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)

  case class TestClientTimeoutException(msg: String) extends Exception

  /**
   * Performs a http request.
   *
   * @param request the request to be performed.
   * @param timeout the timeout for the request. Default timeout is 5 seconds.
   * @param printFailure If true, the response body will be printed if the request fails.
   *                     This flag is intended to be used for debugging purposes only.
   *                     Since this is unsafe, it is false by default.
   *                     It is unsafe because the the response body can only be unmarshalled (i.e. printed) to a string once.
   *                     It will fail if the test code is also unmarshalling the response.
   * @return the response.
   */
  def singleAwaitingRequest(
    request: pekko.http.scaladsl.model.HttpRequest,
    timeout: Option[zio.Duration] = None,
    printFailure: Boolean = false,
  ): Task[pekko.http.scaladsl.model.HttpResponse] =
    ZIO
      .fromFuture[pekko.http.scaladsl.model.HttpResponse](_ =>
        pekko.http.scaladsl.Http().singleRequest(request).map { resp =>
          if (printFailure && resp.status.isFailure()) {
            Unmarshal(resp.entity).to[String].map { body =>
              println(s"Request failed with status ${resp.status} and body $body")
            }
          }
          resp
        },
      )
      .timeout(timeout.getOrElse(10.seconds))
      .some
      .mapError {
        case None            => throw AssertionException("Request timed out.")
        case Some(throwable) => throw throwable
      }

  /**
   * Performs a http request and returns the body of the response.
   */
  def getResponseString(request: pekko.http.scaladsl.model.HttpRequest): Task[String] =
    for {
      response <- singleAwaitingRequest(request)
      body <-
        ZIO
          .attemptBlocking(
            Await.result(
              response.entity.toStrict(FiniteDuration(1, TimeUnit.SECONDS)).map(_.data.decodeString("UTF-8")),
              FiniteDuration(1, TimeUnit.SECONDS),
            ),
          )
          .mapError(error =>
            throw AssertionException(s"Got HTTP ${response.status.intValue}\n REQUEST: $request, \n RESPONSE: $error"),
          )
      _ <- ZIO
             .fail(AssertionException(s"Got HTTP ${response.status.intValue}\n REQUEST: $request, \n RESPONSE: $body"))
             .when(response.status.isFailure())
    } yield body

  /**
   * Performs a http request and dosn't return the string (only error channel).
   */
  def checkResponseOK(request: pekko.http.scaladsl.model.HttpRequest): Task[Unit] =
    for {
      _ <- getResponseString(request)
    } yield ()

  /**
   * Performs a http request and tries to parse the response body as Json.
   */
  def getResponseJson(request: pekko.http.scaladsl.model.HttpRequest): Task[JsObject] =
    for {
      body <- getResponseString(request)
      json <- ZIO.succeed(body.parseJson.asJsObject)
    } yield json

  /**
   * Performs a http request and tries to parse the response body as JsonLD.
   */
  def getResponseJsonLD(request: pekko.http.scaladsl.model.HttpRequest): Task[JsonLDDocument] =
    for {
      body <- getResponseString(request)
      json <- ZIO.succeed(JsonLDUtil.parseJsonLD(body))
    } yield json

  /**
   * Uploads a file to the IIIF Service's "upload" route and returns the information in Sipi's response.
   * The upload creates a multipart/form-data request which can contain multiple files.
   *
   * @param loginToken    the login token to be included in the request to Sipi.
   * @param filesToUpload the files to be uploaded.
   * @return a [[SipiUploadResponse]] representing Sipi's response.
   */
  def uploadToSipi(loginToken: String, filesToUpload: Seq[FileToUpload]): Task[SipiUploadResponse] = {

    // builds the url for the operation
    def uploadUrl(token: String) = ZIO.succeed(s"${config.sipi.internalBaseUrl}/upload?token=$token")

    // create the entity builder
    val builder: MultipartEntityBuilder = MultipartEntityBuilder.create()
    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)

    // add each file to the entity builder
    filesToUpload.foreach { fileToUpload =>
      builder.addBinaryBody(
        "file",
        fileToUpload.path.toFile(),
        fileToUpload.mimeType,
        fileToUpload.path.getFileName.toString,
      )
    }

    // build our entity
    val requestEntity: http.HttpEntity = builder.build()

    // build the request
    def request(url: String, requestEntity: http.HttpEntity) = {
      val req = new http.client.methods.HttpPost(url)
      req.setEntity(requestEntity)
      req
    }

    for {
      url          <- uploadUrl(loginToken)
      entity       <- ZIO.succeed(requestEntity)
      req          <- ZIO.succeed(request(url, entity))
      response     <- doSipiRequest(req)
      sipiResponse <- ZIO.succeed(response.parseJson.asJsObject.convertTo[SipiUploadResponse])
    } yield sipiResponse
  }

  /**
   * Uploads a file to the IIIF Service's "upload_without_processing" route and returns the information in Sipi's response.
   * The upload creates a multipart/form-data request which can contain multiple files.
   *
   * @param loginToken    the login token to be included in the request to Sipi.
   * @param filesToUpload the files to be uploaded.
   * @return a [[SipiUploadWithoutProcessingResponse]] representing Sipi's response.
   */
  def uploadWithoutProcessingToSipi(
    loginToken: String,
    filesToUpload: Seq[FileToUpload],
  ): Task[SipiUploadWithoutProcessingResponse] = {

    // builds the url for the operation
    def uploadWithoutProcessingUrl(token: String) =
      ZIO.succeed(s"${config.sipi.internalBaseUrl}/upload_without_processing?token=$token")

    // create the entity builder
    val builder: MultipartEntityBuilder = MultipartEntityBuilder.create()
    builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE)

    // add each file to the entity builder
    filesToUpload.foreach { fileToUpload =>
      builder.addBinaryBody(
        "file",
        fileToUpload.path.toFile(),
        fileToUpload.mimeType,
        fileToUpload.path.getFileName.toString,
      )
    }

    // build our entity
    val requestEntity: http.HttpEntity = builder.build()

    // build the request
    def request(url: String, requestEntity: http.HttpEntity) = {
      val req = new http.client.methods.HttpPost(url)
      req.setEntity(requestEntity)
      req
    }

    for {
      url          <- uploadWithoutProcessingUrl(loginToken)
      entity       <- ZIO.succeed(requestEntity)
      req          <- ZIO.succeed(request(url, entity))
      response     <- doSipiRequest(req)
      sipiResponse <- ZIO.succeed(response.parseJson.asJsObject.convertTo[SipiUploadWithoutProcessingResponse])
    } yield sipiResponse
  }

  /**
   * Makes an HTTP request to Sipi and returns the response.
   *
   * @param request the HTTP request.
   * @return Sipi's response.
   */
  private def doSipiRequest(request: http.HttpRequest): Task[String] = {
    val targetHost: HttpHost =
      new HttpHost(config.sipi.internalHost, config.sipi.internalPort, config.sipi.internalProtocol)
    val httpContext: HttpClientContext               = HttpClientContext.create()
    var maybeResponse: Option[CloseableHttpResponse] = None

    val response: Task[String] = ZIO.attemptBlocking {
      maybeResponse = Some(httpClient.execute(targetHost, request, httpContext))

      val responseEntityStr: String = Option(maybeResponse.get.getEntity) match {
        case Some(responseEntity) => EntityUtils.toString(responseEntity)
        case None                 => ""
      }

      val statusCode: Int     = maybeResponse.get.getStatusLine.getStatusCode
      val statusCategory: Int = statusCode / 100

      // Was the request successful?
      if (statusCategory == 2) {
        // Yes.
        responseEntityStr
      } else {
        // No. Throw an appropriate exception.
        val sipiErrorMsg = SipiUtil.getSipiErrorMessage(responseEntityStr)

        if (statusCode == 404) {
          throw NotFoundException(sipiErrorMsg)
        } else if (statusCategory == 4) {
          throw BadRequestException(s"Sipi responded with HTTP status code $statusCode: $sipiErrorMsg")
        } else {
          throw SipiException(s"Sipi responded with HTTP status code $statusCode: $sipiErrorMsg")
        }
      }
    }

    maybeResponse match {
      case Some(response) => response.close()
      case None           => ()
    }

    response
  }
}

object TestClientService {

  /**
   * Acquires a configured httpClient, backed by a connection pool,
   * to be used in communicating with SIPI.
   */
  private val acquire = ZIO.attemptBlocking {

    // Create a connection manager with custom configuration.
    val connManager: PoolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager()

    // Create socket configuration
    val socketConfig: SocketConfig = SocketConfig
      .custom()
      .setTcpNoDelay(true)
      .build()

    // Configure the connection manager to use socket configuration by default.
    connManager.setDefaultSocketConfig(socketConfig)

    // Validate connections after 1 sec of inactivity
    connManager.setValidateAfterInactivity(1000)

    // Configure total max or per route limits for persistent connections
    // that can be kept in the pool or leased by the connection manager.
    connManager.setMaxTotal(100)
    connManager.setDefaultMaxPerRoute(10)

    // Sipi custom default request config
    val sipiTimeoutMillis = 120 * 1000
    val defaultRequestConfig = RequestConfig
      .custom()
      .setConnectTimeout(sipiTimeoutMillis)
      .setConnectionRequestTimeout(sipiTimeoutMillis)
      .setSocketTimeout(sipiTimeoutMillis)
      .build()

    // Create an HttpClient with the given custom dependencies and configuration.
    val httpClient: CloseableHttpClient = HttpClients
      .custom()
      .setConnectionManager(connManager)
      .setDefaultRequestConfig(defaultRequestConfig)
      .build()

    httpClient
  }.tap(_ => ZIO.logDebug(">>> Acquire Test Client Service <<<")).orDie

  /**
   * Releases the httpClient, freeing all resources.
   */
  private def release(httpClient: CloseableHttpClient)(implicit system: ActorSystem) = ZIO.attemptBlocking {
    pekko.http.scaladsl.Http().shutdownAllConnectionPools()
    httpClient.close()
  }.tap(_ => ZIO.logDebug(">>> Release Test Client Service <<<")).orDie

  def layer: ZLayer[ActorSystem & AppConfig, Nothing, TestClientService] =
    ZLayer.scoped {
      for {
        sys        <- ZIO.service[ActorSystem]
        config     <- ZIO.service[AppConfig]
        httpClient <- ZIO.acquireRelease(acquire)(release(_)(sys))
      } yield TestClientService(config, httpClient)(sys)
    }

}
