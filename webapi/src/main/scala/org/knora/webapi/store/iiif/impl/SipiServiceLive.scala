/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
import org.apache.http.client.methods.*
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.SocketConfig
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicHeader
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import spray.json.*
import zio.*
import zio.json.DecoderOps
import zio.nio.file.Path

import java.net.URI
import java.util

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.Sipi
import org.knora.webapi.messages.store.sipimessages.*
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.routing.Jwt
import org.knora.webapi.routing.JwtService
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.Asset
import org.knora.webapi.slice.admin.domain.service.DspIngestClient
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.errors.SipiException
import org.knora.webapi.util.SipiUtil
import org.knora.webapi.util.ZScopedJavaIoStreams

/**
 * Makes requests to Sipi.
 *
 * @param sipiConfig   The application's configuration
 * @param jwtService         The JWT Service to handle JWT Tokens
 * @param httpClient  The HTTP Client
 */
final case class SipiServiceLive(
  private val sipiConfig: Sipi,
  private val jwtService: JwtService,
  private val httpClient: CloseableHttpClient,
  private val dspIngestClient: DspIngestClient
) extends SipiService {

  private object SipiRoutes {
    def file(asset: Asset): UIO[URI]           = makeUri(s"${assetBase(asset)}/file")
    def knoraJson(asset: Asset): UIO[URI]      = makeUri(s"${assetBase(asset)}/knora.json")
    private def makeUri(uri: String): UIO[URI] = ZIO.attempt(URI.create(uri)).logError.orDie
    private def assetBase(asset: Asset): String =
      s"${sipiConfig.internalBaseUrl}/${asset.belongsToProject.value}/${asset.internalFilename}"
  }

  /**
   * Asks Sipi for metadata about a file, served from the 'knora.json' route.
   *
   * @param filename the file name
   * @return a [[FileMetadataSipiResponse]] containing the requested metadata.
   */
  override def getFileMetadataFromSipiTemp(filename: String): Task[FileMetadataSipiResponse] =
    for {
      jwt     <- jwtService.createJwt(KnoraSystemInstances.Users.SystemUser)
      request  = new HttpGet(s"${sipiConfig.internalBaseUrl}/tmp/$filename/knora.json")
      _        = request.addHeader(new BasicHeader("Authorization", s"Bearer ${jwt.jwtString}"))
      bodyStr <- doSipiRequest(request)
      res <- ZIO
               .fromEither(bodyStr.fromJson[FileMetadataSipiResponse])
               .mapError(e => SipiException(s"Invalid response from Sipi: $e, $bodyStr"))
    } yield res

  override def getFileMetadataFromDspIngest(shortcode: Shortcode, assetId: AssetId): Task[FileMetadataSipiResponse] =
    for {
      response <- dspIngestClient.getAssetInfo(shortcode, assetId)
    } yield FileMetadataSipiResponse(
      Some(response.originalFilename),
      response.originalMimeType,
      response.internalMimeType.getOrElse("application/octet-stream"),
      response.width,
      response.height,
      None,
      response.duration.map(BigDecimal(_)),
      response.fps.map(BigDecimal(_))
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
    val jwt = jwtService.createJwt(
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

    // builds the url for the operation
    def moveFileUrl(token: String) =
      ZIO.succeed(s"${sipiConfig.internalBaseUrl}/${sipiConfig.moveFileRoute}?token=$token")

    // build the form to send together with the request
    val formParams = new util.ArrayList[NameValuePair]()
    formParams.add(new BasicNameValuePair("filename", moveTemporaryFileToPermanentStorageRequestV2.internalFilename))
    formParams.add(new BasicNameValuePair("prefix", moveTemporaryFileToPermanentStorageRequestV2.prefix))
    val requestEntity = new UrlEncodedFormEntity(formParams, Consts.UTF_8)

    // build the request
    def request(url: String, requestEntity: UrlEncodedFormEntity) = {
      val req = new HttpPost(url)
      req.setEntity(requestEntity)
      req
    }

    for {
      token   <- jwt
      url     <- moveFileUrl(token.jwtString)
      entity  <- ZIO.succeed(requestEntity)
      request <- ZIO.succeed(request(url, entity))
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

    val jwt = jwtService.createJwt(
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

    def deleteUrl(token: String): Task[String] =
      ZIO.succeed(
        s"${sipiConfig.internalBaseUrl}/${sipiConfig.deleteTempFileRoute}/${deleteTemporaryFileRequestV2.internalFilename}?token=$token"
      )

    for {
      token   <- jwt
      url     <- deleteUrl(token.jwtString)
      request <- ZIO.succeed(new HttpDelete(url))
      _       <- doSipiRequest(request)
    } yield SuccessResponseV2("Deleted temporary file.")
  }

  /**
   * Asks Sipi for a text file used internally by Knora.
   *
   * @param textFileRequest the request message.
   */
  def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse] = {

    // helper method to handle errors
    def handleErrors(ex: Throwable) = ex match {
      case notFoundException: NotFoundException =>
        ZIO.die(
          NotFoundException(
            s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${notFoundException.message}"
          )
        )

      case badRequestException: BadRequestException =>
        ZIO.die(
          SipiException(
            s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${badRequestException.message}"
          )
        )

      case sipiException: SipiException =>
        ZIO.die(
          SipiException(
            s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${sipiException.message}",
            sipiException.cause
          )
        )

      case other =>
        ZIO.logError(
          s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${other.getMessage}"
        ) *>
          ZIO.die(
            SipiException(
              s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${other.getMessage}"
            )
          )
    }

    for {
      request     <- ZIO.succeed(new HttpGet(textFileRequest.fileUrl))
      responseStr <- doSipiRequest(request).catchAll(ex => handleErrors(ex))
    } yield SipiGetTextFileResponse(responseStr)
  }

  /**
   * Tries to access the IIIF Service to check if Sipi is running.
   */
  def getStatus(): Task[IIIFServiceStatusResponse] =
    for {
      request  <- ZIO.succeed(new HttpGet(sipiConfig.internalBaseUrl + "/server/test.html"))
      response <- doSipiRequest(request).fold(_ => IIIFServiceStatusNOK, _ => IIIFServiceStatusOK)
    } yield response

  /**
   * Makes an HTTP request to Sipi and returns the response.
   *
   * @param request the HTTP request.
   * @return Sipi's response.
   */
  private def doSipiRequest(request: HttpRequest): Task[String] = {
    val targetHost: HttpHost =
      new HttpHost(sipiConfig.internalHost, sipiConfig.internalPort, sipiConfig.internalProtocol)
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

    sipiRequest.catchAll {
      case badRequestException: BadRequestException => ZIO.fail(badRequestException)
      case notFoundException: NotFoundException     => ZIO.fail(notFoundException)
      case sipiException: SipiException             => ZIO.fail(sipiException)
      case e: Exception                             => ZIO.logError(e.getMessage) *> ZIO.fail(SipiException("Failed to connect to Sipi", e))
    }
  }

  /**
   * Downloads an asset and its knora.json from Sipi.
   *
   * @param asset     The asset to download.
   * @param targetDir The target directory in which the asset should be stored.
   * @param user      The user who is downloading the asset.
   * @return The path to the downloaded asset. If the asset could not be downloaded, [[None]] is returned.
   */
  override def downloadAsset(asset: Asset, targetDir: Path, user: User): Task[Option[Path]] = {
    def statusCode(response: HttpResponse): Int = response.getStatusLine.getStatusCode
    def executeDownloadRequest(uri: URI, jwt: Jwt, targetFilename: String) = ZIO.scoped {
      makeGetRequestWithAuthorization(uri, jwt).flatMap {
        sendRequest(_)
          .filterOrElseWith(statusCode(_) == 200)(it => ZIO.fail(new Exception(s"${statusCode(it)} code from sipi")))
          .flatMap(response => saveToFile(response.getEntity, targetDir / targetFilename))
          .tapBoth(
            e => ZIO.logWarning(s"Failed downloading $uri: ${e.getMessage}"),
            _ => ZIO.logDebug(s"Downloaded $uri")
          )
          .fold(_ => None, Some(_))
      }
    }
    def downloadAsset(asset: Asset, jwt: Jwt): Task[Option[Path]] =
      ZIO.logDebug(s"Downloading ${Asset.logString(asset)}") *>
        SipiRoutes.file(asset).flatMap(executeDownloadRequest(_, jwt, asset.internalFilename))
    def downloadKnoraJson(asset: Asset, jwt: Jwt): Task[Option[Path]] =
      ZIO.logDebug(s"Downloading knora.json for  ${Asset.logString(asset)}") *>
        SipiRoutes.knoraJson(asset).flatMap(executeDownloadRequest(_, jwt, s"${asset.internalFilename}_knora.json"))

    for {
      jwt             <- jwtService.createJwt(user)
      assetDownloaded <- downloadAsset(asset, jwt)
      _               <- downloadKnoraJson(asset, jwt).when(assetDownloaded.isDefined)
    } yield assetDownloaded
  }

  private def makeGetRequestWithAuthorization(uri: URI, jwt: Jwt): UIO[HttpGet] = {
    val request = new HttpGet(uri)
    addAuthHeader(request, jwt)
    ZIO.succeed(request)
  }

  private def addAuthHeader(request: HttpUriRequest, jwt: Jwt): Unit =
    request.addHeader("Authorization", s"Bearer ${jwt.jwtString}")

  private def sendRequest(request: HttpUriRequest): ZIO[Scope, Throwable, HttpResponse] = {
    def acquire = ZIO
      .attemptBlocking(httpClient.execute(request))
      .tapErrorTrace(it => ZIO.logError(s"Failed to execute request $request: ${it._1}\n${it._2}}"))

    def release(response: CloseableHttpResponse) = ZIO.attempt(response.close()).logError.ignore

    ZIO.acquireRelease(acquire)(release)
  }

  private def saveToFile(entity: HttpEntity, targetFile: Path) =
    ZScopedJavaIoStreams
      .fileOutputStream(targetFile)
      .flatMap(out => ZIO.attemptBlocking(entity.getContent.transferTo(out)))
      .as(targetFile)
}

object SipiServiceLive {

  /**
   * Acquires a configured httpClient, backed by a connection pool,
   * to be used in communicating with SIPI.
   */
  private def acquire(sipiConfig: Sipi): UIO[CloseableHttpClient] = ZIO.attemptBlocking {

    // timeout from config
    val sipiTimeoutMillis = sipiConfig.timeout.toMillis.toInt

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

  val layer: URLayer[AppConfig & DspIngestClient & JwtService, SipiService] =
    ZLayer.scoped {
      for {
        config          <- ZIO.serviceWith[AppConfig](_.sipi)
        jwtService      <- ZIO.service[JwtService]
        httpClient      <- ZIO.acquireRelease(acquire(config))(release)
        dspIngestClient <- ZIO.service[DspIngestClient]
      } yield SipiServiceLive(config, jwtService, httpClient, dspIngestClient)
    }
}
