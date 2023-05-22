/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.impl

import org.apache.http.Consts
import org.apache.http.HttpEntity
import org.apache.http.HttpHost
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.NameValuePair
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.SocketConfig
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import spray.json._
import zio._
import zio.nio.file.Path
import java.net.URI
import java.util

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.Sipi
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.routing.Jwt
import org.knora.webapi.routing.JwtService
import org.knora.webapi.slice.admin.domain.service.Asset
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.iiif.domain.SipiKnoraJsonResponseProtocol.sipiKnoraJsonResponseFormat
import org.knora.webapi.store.iiif.domain._
import org.knora.webapi.store.iiif.errors.SipiException
import org.knora.webapi.util.SipiUtil
import org.knora.webapi.util.ZScopedJavaIoStreams

/**
 * Makes requests to Sipi.
 *
 * @param sipiConf    The application's configuration for Sipi
 * @param jwtService         The JWT Service to handle JWT Tokens
 * @param httpClient  The HTTP Client
 */
final case class IIIFServiceSipiImpl(
  private val sipiConf: Sipi,
  private val jwtService: JwtService,
  private val httpClient: CloseableHttpClient
) extends IIIFService {

  /**
   * Asks Sipi for metadata about a file, served from the 'knora.json' route.
   *
   * @param getFileMetadataRequest the request.
   * @return a [[GetFileMetadataResponse]] containing the requested metadata.
   */
  def getFileMetadata(getFileMetadataRequest: GetFileMetadataRequest): Task[GetFileMetadataResponse] =
    for {
      jwt             <- jwtService.createJwt(getFileMetadataRequest.requestingUser)
      uri             <- SipiRoutes.fileMetadataRequest(getFileMetadataRequest)
      request         <- makeGetRequestWithAuthorization(uri, jwt)
      sipiResponseStr <- doSipiRequest(request)
      sipiResponse    <- ZIO.attempt(sipiResponseStr.parseJson.convertTo[SipiKnoraJsonResponse])
    } yield GetFileMetadataResponse(
      originalFilename = sipiResponse.originalFilename,
      originalMimeType = sipiResponse.originalMimeType,
      internalMimeType = sipiResponse.internalMimeType,
      width = sipiResponse.width,
      height = sipiResponse.height,
      pageCount = sipiResponse.numpages,
      duration = sipiResponse.duration,
      fps = sipiResponse.fps
    )

  /**
   * Asks Sipi to move a file from temporary storage to permanent storage.
   *
   * @param moveTemporaryFileToPermanentStorageRequestV2 the request.
   * @return a [[SuccessResponseV2]].
   */
  def moveTemporaryFileToPermanentStorage(
    moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest
  ): Task[SuccessResponseV2] = {
    // create the JWT token with the necessary permission
    val createJwt = jwtService.createJwt(
      moveTemporaryFileToPermanentStorageRequestV2.requestingUser,
      Map(
        "knora-data" -> JsObject(
          Map(
            "permission" -> JsString("StoreFile"),
            "filename"   -> JsString(moveTemporaryFileToPermanentStorageRequestV2.internalFilename),
            "prefix"     -> JsString(moveTemporaryFileToPermanentStorageRequestV2.prefix)
          )
        )
      )
    )

    // build the form to send together with the request
    val formParams = new util.ArrayList[NameValuePair]()
    formParams.add(new BasicNameValuePair("filename", moveTemporaryFileToPermanentStorageRequestV2.internalFilename))
    formParams.add(new BasicNameValuePair("prefix", moveTemporaryFileToPermanentStorageRequestV2.prefix))

    for {
      jwt     <- createJwt
      url     <- SipiRoutes.moveTemporaryFileToPermanentStorageUrl()
      request <- makePostRequestWithAuthorization(url, new UrlEncodedFormEntity(formParams, Consts.UTF_8), jwt)
      _       <- doSipiRequest(request)
    } yield SuccessResponseV2("Moved file to permanent storage.")
  }

  /**
   * Asks Sipi to delete a temporary file.
   *
   * @param deleteTemporaryFileRequestV2 the request.
   * @return a [[SuccessResponseV2]].
   */
  def deleteTemporaryFile(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest): Task[SuccessResponseV2] = {
    val createJwt = jwtService.createJwt(
      deleteTemporaryFileRequestV2.requestingUser,
      Map(
        "knora-data" -> JsObject(
          Map(
            "permission" -> JsString("DeleteTempFile"),
            "filename"   -> JsString(deleteTemporaryFileRequestV2.internalFilename)
          )
        )
      )
    )

    for {
      jwt     <- createJwt
      uri     <- SipiRoutes.deleteTempFile(deleteTemporaryFileRequestV2)
      request <- makeDeleteRequestWithAuthorization(uri, jwt)
      _       <- doSipiRequest(request)
    } yield SuccessResponseV2("Deleted temporary file.")
  }

  /**
   * Asks Sipi for a text file used internally by Knora.
   *
   * @param textFileRequest the request message.
   */
  def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse] =
    for {
      jwt         <- jwtService.createJwt(textFileRequest.requestingUser)
      request     <- makeGetRequestWithAuthorization(URI.create(textFileRequest.fileUrl), jwt)
      responseStr <- doSipiRequest(request)
    } yield SipiGetTextFileResponse(responseStr)

  /**
   * Tries to access the IIIF Service to check if Sipi is running.
   */
  def getStatus(): Task[IIIFServiceStatusResponse] =
    for {
      request  <- ZIO.succeed(new HttpGet(sipiConf.internalBaseUrl + "/server/test.html"))
      response <- doSipiRequest(request).fold(_ => IIIFServiceStatusNOK, _ => IIIFServiceStatusOK)
    } yield response

  /**
   * Makes an HTTP request to Sipi and returns the response.
   *
   * @param request the HTTP request.
   * @return Sipi's response.
   */
  private def doSipiRequest(request: HttpUriRequest): Task[String] = ZIO.scoped {
    sendRequest(request).flatMap { response =>
      val r= request
      val statusCode     = response.getStatusLine.getStatusCode
      val statusCategory = statusCode / 100
      val bodyString     = EntityUtils.toString(response.getEntity)
      if (statusCategory == 2) {
        ZIO.succeed(bodyString)
      } else {
        val sipiErrorMsg = SipiUtil.getSipiErrorMessage(bodyString)
        if (statusCode == 404) {
          ZIO.fail(NotFoundException(sipiErrorMsg))
        } else if (statusCategory == 4) {
          ZIO.fail(BadRequestException(s"Sipi responded with HTTP status code $statusCode: $sipiErrorMsg"))
        } else {
          ZIO.fail(SipiException(s"Sipi responded with HTTP status code $statusCode: $sipiErrorMsg")).logError
        }
      }
    }
  }

  private object SipiRoutes {
    def moveTemporaryFileToPermanentStorageUrl(): Task[URI] =
      ZIO.attempt(URI.create(s"${sipiConf.internalBaseUrl}/${sipiConf.moveFileRoute}"))

    def deleteTempFile(request: DeleteTemporaryFileRequest): Task[URI] =
      ZIO.attempt(
        URI.create(s"${sipiConf.internalBaseUrl}/${sipiConf.deleteTempFileRoute}/${request.internalFilename}")
      )

    def fileMetadataRequest(request: GetFileMetadataRequest) =
      ZIO.attempt(URI.create(s"${sipiConf.internalBaseUrl}${request.filePath}/knora.json"))

    def file(asset: Asset): Task[URI] = file(asset.belongsToProject.shortcode, asset.internalFilename)
    private def file(projectShortcode: String, internalFilename: String): Task[URI] =
      ZIO.attempt(URI.create(s"${sipiConf.internalBaseUrl}/$projectShortcode/$internalFilename/file"))
  }

  private def makeGetRequestWithAuthorization(uri: URI, jwt: Jwt): UIO[HttpGet] = {
    val request = new HttpGet(uri)
    addAuthHeader(request, jwt)
    ZIO.succeed(request)
  }

  private def addAuthHeader(request: HttpRequestBase, jwt: Jwt): Unit =
    request.addHeader("Authorization", s"Bearer ${jwt.jwtString}")

  private def makePostRequestWithAuthorization(uri: URI, entity: UrlEncodedFormEntity, jwt: Jwt): UIO[HttpPost] = {
    val request = new HttpPost(uri)
    request.setEntity(entity)
    addAuthHeader(request, jwt)
    ZIO.succeed(request)
  }

  private def makeDeleteRequestWithAuthorization(uri: URI, jwt: Jwt): UIO[HttpDelete] = {
    val request = new HttpDelete(uri)
    addAuthHeader(request, jwt)
    ZIO.succeed(request)
  }

  override def downloadAsset(asset: Asset, targetDir: Path, user: UserADM): Task[Option[Path]] = {

    def code(response: HttpResponse): Int = response.getStatusLine.getStatusCode

    for {
      jwt     <- jwtService.createJwt(user)
      uri     <- SipiRoutes.file(asset)
      request <- makeGetRequestWithAuthorization(uri, jwt)
      downloaded <- ZIO.scoped {
                      sendRequest(request)
                        .filterOrElseWith(code(_) == 200)(it => ZIO.fail(new Exception(s"${code(it)} code from sipi")))
                        .flatMap(response => saveToFile(asset, response.getEntity, targetDir))
                        .tapBoth(
                          e => ZIO.logWarning(s"Failed downloading ${Asset.logString(asset)}: ${e.getMessage}"),
                          _.toAbsolutePath.flatMap(p => ZIO.logInfo(s"Downloaded ${Asset.logString(asset)} to $p"))
                        )
                        .fold(_ => None, Some(_))
                    }
    } yield downloaded
  }

  private def sendRequest(request: HttpUriRequest): ZIO[Scope, Throwable, HttpResponse] = {
    def acquire = ZIO
      .attemptBlocking(httpClient.execute(request))
      .tapErrorTrace(it =>
        ZIO.logError(s"Failed to execute request $request: ${it._1}\n${it._2}}")
      )
    def release(response: CloseableHttpResponse) = ZIO.attempt(response.close()).logError.ignore
    ZIO.acquireRelease(acquire)(release)
  }

  private def saveToFile(asset: Asset, entity: HttpEntity, targetDir: Path): ZIO[Scope, Throwable, Path] = {
    val targetFile = targetDir / asset.internalFilename
    for {
      out <- ZScopedJavaIoStreams.fileOutputStream(targetFile)
      _   <- ZIO.attemptBlocking(entity.getContent.transferTo(out))
    } yield targetFile
  }
}

object IIIFServiceSipiImpl {

  /**
   * Acquires a configured httpClient, backed by a connection pool,
   * to be used in communicating with SIPI.
   */
  private def acquire(config: Sipi): UIO[CloseableHttpClient] = ZIO.attemptBlocking {

    // timeout from config
    val sipiTimeoutMillis: Int = config.timeoutInSeconds.toMillis.toInt

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
    val defaultRequestConfig = RequestConfig
      .custom()
      .setConnectTimeout(sipiTimeoutMillis)
      .setConnectionRequestTimeout(sipiTimeoutMillis)
      .setSocketTimeout(sipiTimeoutMillis)
      .build()

    // Return an HttpClient with the given custom dependencies and configuration.
    HttpClients
      .custom()
      .setConnectionManager(connManager)
      .setDefaultRequestConfig(defaultRequestConfig)
      .build()
  }.logError.orDie.zipLeft(ZIO.logInfo(">>> Acquire Sipi IIIF Service <<<"))

  /**
   * Releases the httpClient, freeing all resources.
   */
  private def release(httpClient: CloseableHttpClient): UIO[Unit] =
    ZIO.attemptBlocking(httpClient.close()).logError.ignore <* ZIO.logInfo(">>> Release Sipi IIIF Service <<<")

  val layer: URLayer[AppConfig with JwtService, IIIFServiceSipiImpl] =
    ZLayer.scoped {
      for {
        config     <- ZIO.serviceWith[AppConfig](_.sipi)
        jwtService <- ZIO.service[JwtService]
        httpClient <- ZIO.acquireRelease(acquire(config))(release)
      } yield IIIFServiceSipiImpl(config, jwtService, httpClient)
    }
}
