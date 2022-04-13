/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.impl

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{CloseableHttpResponse, HttpDelete, HttpGet, HttpPost}
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.config.SocketConfig
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.message.BasicNameValuePair
import org.apache.http.protocol.HttpContext
import org.apache.http.util.EntityUtils
import org.apache.http.{Consts, HttpHost, HttpRequest, NameValuePair, NoHttpResponseException}
import org.knora.webapi.exceptions.{BadRequestException, NotFoundException, SipiException}
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.routing.JWTHelper
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings}
import org.knora.webapi.util.ActorUtil.{handleUnexpectedMessage, try2Message}
import org.knora.webapi.util.SipiUtil
import spray.json._

import java.io.IOException
import java.util
import scala.concurrent.ExecutionContext
import scala.util.Try
import org.knora.webapi.store.iiif.api.IIIFService
import zio._
import org.knora.webapi.store.iiif.config.SipiConfig
import org.knora.webapi.store.iiif.domain._
import org.knora.webapi.auth.JWTService

/**
 * Makes requests to Sipi.
 */
case class IIIFServiceSipiImpl(sipiConfig: SipiConfig, jwt: JWTService, httpClient: CloseableHttpClient)
    extends IIIFService {

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  /**
   * Asks Sipi for metadata about a file, served from the 'knora.json' route.
   *
   * @param getFileMetadataRequest the request.
   * @return a [[GetFileMetadataResponse]] containing the requested metadata.
   */
  def getFileMetadata(getFileMetadataRequest: GetFileMetadataRequest): Task[GetFileMetadataResponse] = {
    import SipiKnoraJsonResponseProtocol._

    val knoraInfoUrl = getFileMetadataRequest.fileUrl + "/knora.json"
    val sipiRequest  = new HttpGet(knoraInfoUrl)

    for {
      sipiResponseStr <- doSipiRequest(sipiRequest)
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
  }

  /**
   * Asks Sipi to move a file from temporary storage to permanent storage.
   *
   * @param moveTemporaryFileToPermanentStorageRequestV2 the request.
   * @return a [[SuccessResponseV2]].
   */
  def moveTemporaryFileToPermanentStorage(
    moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest
  ): Task[SuccessResponseV2] = {

    val token: UIO[String] = jwt.newToken(
      moveTemporaryFileToPermanentStorageRequestV2.requestingUser.id,
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

    val moveFileUrl = s"${sipiConfig.internal.baseUrl}/${sipiConfig.v2.moveFileRoute}?token=$token"

    val formParams = new util.ArrayList[NameValuePair]()
    formParams.add(new BasicNameValuePair("filename", moveTemporaryFileToPermanentStorageRequestV2.internalFilename))
    formParams.add(new BasicNameValuePair("prefix", moveTemporaryFileToPermanentStorageRequestV2.prefix))
    val requestEntity = new UrlEncodedFormEntity(formParams, Consts.UTF_8)
    val queryHttpPost = new HttpPost(moveFileUrl)
    queryHttpPost.setEntity(requestEntity)

    for {
      _ <- doSipiRequest(queryHttpPost)
    } yield SuccessResponseV2("Moved file to permanent storage.")
  }

  /**
   * Asks Sipi to delete a temporary file.
   *
   * @param deleteTemporaryFileRequestV2 the request.
   * @return a [[SuccessResponseV2]].
   */
  def deleteTemporaryFile(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest): Task[SuccessResponseV2] = {
    val token: UIO[String] = jwt.newToken(
      deleteTemporaryFileRequestV2.requestingUser.id,
      Map(
        "knora-data" -> JsObject(
          Map(
            "permission" -> JsString("DeleteTempFile"),
            "filename"   -> JsString(deleteTemporaryFileRequestV2.internalFilename)
          )
        )
      )
    )

    val deleteFileUrl =
      s"${sipiConfig.internal.baseUrl}/${sipiConfig.v2.deleteTempFileRoute}/${deleteTemporaryFileRequestV2.internalFilename}?token=$token"
    val request = new HttpDelete(deleteFileUrl)

    for {
      _ <- doSipiRequest(request)
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
        ZIO.fail(
          NotFoundException(
            s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${notFoundException.message}"
          )
        )

      case badRequestException: BadRequestException =>
        ZIO.fail(
          SipiException(
            s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${badRequestException.message}"
          )
        )

      case sipiException: SipiException =>
        ZIO.fail(
          SipiException(
            s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${sipiException.message}",
            sipiException.cause
          )
        )

      case other =>
        ZIO.logError(
          s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${other.getMessage}"
        ) *>
          ZIO.fail(
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
  def getStatus(): UIO[IIIFServiceStatusResponse] = {
    val request = new HttpGet(sipiConfig.internal.baseUrl + "/server/test.html")
    doSipiRequest(request).fold(_ => IIIFServiceStatusNOK, _ => IIIFServiceStatusOK)
  }

  /**
   * Makes an HTTP request to Sipi and returns the response.
   *
   * @param request the HTTP request.
   * @return Sipi's response.
   */
  private def doSipiRequest(request: HttpRequest): Task[String] = {
    val targetHost: HttpHost =
      new HttpHost(sipiConfig.external.host, sipiConfig.external.port, sipiConfig.external.protocol)
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

object IIIFServiceSipiImpl {

  /**
   * Acquires a configured httpClient, backed by a connection pool,
   * to be used in communicating with SIPI.
   */
  private def aquire(config: SipiConfig) = ZIO.attemptBlocking {

    // timeout from config
    val sipiTimeoutMillis = config.timeout.toMillis.toInt

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
  }.orDie

  /**
   * Releases the httpClient, freeing all resources.
   */
  private def release(httpClient: CloseableHttpClient) = ZIO.attemptBlocking {
    httpClient.close()
  }.orDie

  val layer: ZLayer[SipiConfig & JWTService, Nothing, IIIFService] = {
    ZLayer.scoped {
      for {
        sipiConfig <- ZIO.service[SipiConfig]
        jwtService <- ZIO.service[JWTService]
        httpClient <- ZIO.acquireRelease(aquire(sipiConfig))(release(_))
      } yield IIIFServiceSipiImpl(sipiConfig, jwtService, httpClient)
    }.tap(_ => ZIO.debug(">>> Sipi IIIF Service Initialized <<<"))
  }
}
