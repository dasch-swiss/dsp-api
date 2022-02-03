/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif

import java.util

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.{CloseableHttpResponse, HttpDelete, HttpGet, HttpPost}
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.apache.http.{Consts, HttpHost, HttpRequest, NameValuePair}
import org.knora.webapi.exceptions.{BadRequestException, NotFoundException, SipiException}
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.routing.JWTHelper
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings}
import org.knora.webapi.util.ActorUtil.{handleUnexpectedMessage, try2Message}
import org.knora.webapi.util.SipiUtil
import spray.json._

import scala.concurrent.ExecutionContext
import scala.util.Try

/**
 * Makes requests to Sipi.
 */
class SipiConnector extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  private val settings = KnoraSettings(system)

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val targetHost: HttpHost = new HttpHost(settings.internalSipiHost, settings.internalSipiPort, "http")

  private val sipiTimeoutMillis = settings.sipiTimeout.toMillis.toInt

  private val sipiRequestConfig = RequestConfig
    .custom()
    .setConnectTimeout(sipiTimeoutMillis)
    .setConnectionRequestTimeout(sipiTimeoutMillis)
    .setSocketTimeout(sipiTimeoutMillis)
    .build()

  private val httpClient: CloseableHttpClient = HttpClients.custom.setDefaultRequestConfig(sipiRequestConfig).build

  override def receive: Receive = {
    case getFileMetadataRequest: GetFileMetadataRequest =>
      try2Message(sender(), getFileMetadata(getFileMetadataRequest), log)
    case moveTemporaryFileToPermanentStorageRequest: MoveTemporaryFileToPermanentStorageRequest =>
      try2Message(sender(), moveTemporaryFileToPermanentStorage(moveTemporaryFileToPermanentStorageRequest), log)
    case deleteTemporaryFileRequest: DeleteTemporaryFileRequest =>
      try2Message(sender(), deleteTemporaryFile(deleteTemporaryFileRequest), log)
    case getTextFileRequest: SipiGetTextFileRequest =>
      try2Message(sender(), sipiGetTextFileRequest(getTextFileRequest), log)
    case IIIFServiceGetStatus => try2Message(sender(), iiifGetStatus(), log)
    case other                => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
  }

  /**
   * Represents a response from Sipi's `knora.json` route.
   *
   * @param originalFilename the file's original filename, if known.
   * @param originalMimeType the file's original MIME type.
   * @param internalMimeType the file's internal MIME type.
   * @param width            the file's width in pixels, if applicable.
   * @param height           the file's height in pixels, if applicable.
   * @param numpages         the number of pages in the file, if applicable.
   * @param duration         the duration of the file in seconds, if applicable.
   */
  case class SipiKnoraJsonResponse(
    originalFilename: Option[String],
    originalMimeType: Option[String],
    internalMimeType: String,
    width: Option[Int],
    height: Option[Int],
    numpages: Option[Int],
    duration: Option[BigDecimal],
    fps: Option[BigDecimal]
  ) {
    if (originalFilename.contains("")) {
      throw SipiException(s"Sipi returned an empty originalFilename")
    }

    if (originalMimeType.contains("")) {
      throw SipiException(s"Sipi returned an empty originalMimeType")
    }
  }

  object SipiKnoraJsonResponseProtocol extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val sipiKnoraJsonResponseFormat: RootJsonFormat[SipiKnoraJsonResponse] = jsonFormat8(SipiKnoraJsonResponse)
  }

  /**
   * Asks Sipi for metadata about a file.
   *
   * @param getFileMetadataRequest the request.
   * @return a [[GetFileMetadataResponse]] containing the requested metadata.
   */
  private def getFileMetadata(getFileMetadataRequest: GetFileMetadataRequest): Try[GetFileMetadataResponse] = {
    import SipiKnoraJsonResponseProtocol._

    val knoraInfoUrl = getFileMetadataRequest.fileUrl + "/knora.json"
    val sipiRequest = new HttpGet(knoraInfoUrl)

    for {
      sipiResponseStr <- doSipiRequest(sipiRequest)
      sipiResponse: SipiKnoraJsonResponse = sipiResponseStr.parseJson.convertTo[SipiKnoraJsonResponse]
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
  private def moveTemporaryFileToPermanentStorage(
    moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest
  ): Try[SuccessResponseV2] = {
    val token: String = JWTHelper.createToken(
      userIri = moveTemporaryFileToPermanentStorageRequestV2.requestingUser.id,
      secret = settings.jwtSecretKey,
      longevity = settings.jwtLongevity,
      content = Map(
        "knora-data" -> JsObject(
          Map(
            "permission" -> JsString("StoreFile"),
            "filename" -> JsString(moveTemporaryFileToPermanentStorageRequestV2.internalFilename),
            "prefix" -> JsString(moveTemporaryFileToPermanentStorageRequestV2.prefix)
          )
        )
      )
    )

    val moveFileUrl = s"${settings.internalSipiBaseUrl}/${settings.sipiMoveFileRouteV2}?token=$token"

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
  private def deleteTemporaryFile(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest): Try[SuccessResponseV2] = {
    val token: String = JWTHelper.createToken(
      userIri = deleteTemporaryFileRequestV2.requestingUser.id,
      secret = settings.jwtSecretKey,
      longevity = settings.jwtLongevity,
      content = Map(
        "knora-data" -> JsObject(
          Map(
            "permission" -> JsString("DeleteTempFile"),
            "filename" -> JsString(deleteTemporaryFileRequestV2.internalFilename)
          )
        )
      )
    )

    val deleteFileUrl =
      s"${settings.internalSipiBaseUrl}/${settings.sipiDeleteTempFileRouteV2}/${deleteTemporaryFileRequestV2.internalFilename}?token=$token"
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
  private def sipiGetTextFileRequest(textFileRequest: SipiGetTextFileRequest): Try[SipiGetTextFileResponse] = {
    val httpRequest = new HttpGet(textFileRequest.fileUrl)

    val sipiResponseTry: Try[SipiGetTextFileResponse] = for {
      responseStr <- doSipiRequest(httpRequest)
    } yield SipiGetTextFileResponse(responseStr)

    sipiResponseTry.recover {
      case notFoundException: NotFoundException =>
        throw NotFoundException(
          s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${notFoundException.message}"
        )

      case badRequestException: BadRequestException =>
        throw SipiException(
          s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${badRequestException.message}"
        )

      case sipiException: SipiException =>
        throw SipiException(
          s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${sipiException.message}",
          sipiException.cause
        )

      case other =>
        throw SipiException(
          s"Unable to get file ${textFileRequest.fileUrl} from Sipi as requested by ${textFileRequest.senderName}: ${other.getMessage}"
        )
    }
  }

  /**
   * Tries to access the IIIF Service.
   */
  private def iiifGetStatus(): Try[IIIFServiceStatusResponse] = {
    val request = new HttpGet(settings.internalSipiBaseUrl + "/server/test.html")
    println(request)

    val result: Try[String] = doSipiRequest(request)
    if (result.isSuccess) {
      Try(IIIFServiceStatusOK)
    } else {
      Try(IIIFServiceStatusNOK)
    }
  }

  /**
   * Makes an HTTP request to Sipi and returns the response.
   *
   * @param request the HTTP request.
   * @return Sipi's response.
   */
  private def doSipiRequest(request: HttpRequest): Try[String] = {
    val httpContext: HttpClientContext = HttpClientContext.create
    var maybeResponse: Option[CloseableHttpResponse] = None

    val sipiResponseTry = Try {
      maybeResponse = Some(httpClient.execute(targetHost, request, httpContext))
      println(maybeResponse)

      val responseEntityStr: String = Option(maybeResponse.get.getEntity) match {
        case Some(responseEntity) => EntityUtils.toString(responseEntity)
        case None                 => ""
      }

      val statusCode: Int = maybeResponse.get.getStatusLine.getStatusCode
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

    sipiResponseTry.recover {
      case badRequestException: BadRequestException => throw badRequestException
      case notFoundException: NotFoundException     => throw notFoundException
      case sipiException: SipiException             => throw sipiException
      case e: Exception                             => throw SipiException("Failed to connect to Sipi", e, log)
    }
  }
}
