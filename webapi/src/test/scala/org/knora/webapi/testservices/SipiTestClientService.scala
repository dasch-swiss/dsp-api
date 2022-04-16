package org.knora.webapi.testservices

import java.nio.file.Path
import spray.json.JsObject
import org.knora.webapi.exceptions.AssertionException
import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await
import java.nio.file.Files
import org.knora.webapi.messages.store.sipimessages.SipiUploadResponse
import zio._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.auth.JWTService
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.config.SocketConfig
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.HttpHost
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.util.EntityUtils
import org.knora.webapi.exceptions.NotFoundException
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.util.SipiUtil
import org.knora.webapi.exceptions.SipiException
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.HttpMultipartMode
import java.io.File
import org.apache.http
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.store.sipimessages.SipiUploadResponseJsonProtocol._
import spray.json._

/**
 * Represents a file to be uploaded to Sipi.
 *
 * @param path     the path of the file.
 * @param mimeType the MIME type of the file.
 */
final case class FileToUpload(path: Path, mimeType: ContentType)

/**
 * Represents an image file to be uploaded to Sipi.
 *
 * @param fileToUpload the file to be uploaded.
 * @param width        the image's width in pixels.
 * @param height       the image's height in pixels.
 */
final case class InputFile(fileToUpload: FileToUpload, width: Int, height: Int)

final case class SipiTestClientService(config: AppConfig, jwt: JWTService, httpClient: CloseableHttpClient) {

  /**
   * Uploads a file to Sipi and returns the information in Sipi's response.
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
        fileToUpload.path.getFileName.toString
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

    val sipiRequest: Task[String] = ZIO.attemptBlocking {
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

    sipiRequest.catchAll(error =>
      error match {
        case badRequestException: BadRequestException => ZIO.fail(badRequestException)
        case notFoundException: NotFoundException     => ZIO.fail(notFoundException)
        case sipiException: SipiException             => ZIO.fail(sipiException)
        case e: Exception                             => ZIO.logError(e.getMessage) *> ZIO.fail(SipiException("Failed to connect to Sipi", e))
      }
    )
  }
}

object SipiTestClientService extends Accessible[SipiTestClientService] {

  /**
   * Acquires a configured httpClient, backed by a connection pool,
   * to be used in communicating with SIPI.
   */
  private def aquire(config: AppConfig) = ZIO.attemptBlocking {

    // timeout from config
    val sipiTimeoutMillis = config.sipi.timeoutInSeconds.toMillis.toInt

    // Create a connection manager with custom configuration.
    val connManager: PoolingHttpClientConnectionManager = new PoolingHttpClientConnectionManager()

    // Create socket configuration
    val socketConfig: SocketConfig = SocketConfig
      .custom()
      .setTcpNoDelay(true)
      .build();

    // Configure the connection manager to use socket configuration by default.
    connManager.setDefaultSocketConfig(socketConfig)

    // Validate connections after 1 sec of inactivity
    connManager.setValidateAfterInactivity(1000);

    // Configure total max or per route limits for persistent connections
    // that can be kept in the pool or leased by the connection manager.
    connManager.setMaxTotal(100)
    connManager.setDefaultMaxPerRoute(10)

    // Sipi custom default request config
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
  }.tap(_ => ZIO.debug(">>> Aquire Sipi Test Client Service <<<")).orDie

  /**
   * Releases the httpClient, freeing all resources.
   */
  private def release(httpClient: CloseableHttpClient) = ZIO.attemptBlocking {
    httpClient.close()
  }.tap(_ => ZIO.debug(">>> Release Sipi Test Client Service <<<")).orDie

  val layer: ZLayer[AppConfig & JWTService, Nothing, SipiTestClientService] = {
    ZLayer {
      for {
        config     <- ZIO.service[AppConfig]
        jwtService <- ZIO.service[JWTService]
        httpClient <- aquire(config)
      } yield SipiTestClientService(config, jwtService, httpClient)
    }.tap(_ => ZIO.debug(">>> Sipi Test Client Service Initialized <<<"))
  }
}
