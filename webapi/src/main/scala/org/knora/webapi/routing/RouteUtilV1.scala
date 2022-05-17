/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.RouteResult
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.exceptions.SipiException
import org.knora.webapi.exceptions.UnexpectedMessageException
import org.knora.webapi.http.status.ApiStatusCodesV1
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.GetFileMetadataResponse
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2.TextWithStandoffTagsV2
import org.knora.webapi.messages.v1.responder.KnoraRequestV1
import org.knora.webapi.messages.v1.responder.KnoraResponseV1
import org.knora.webapi.messages.v1.responder.valuemessages.ArchiveFileValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.AudioFileValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.DocumentFileValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.FileValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.MovingImageFileValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.StillImageFileValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.TextFileValueV1
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingResponseV2
import org.knora.webapi.settings.KnoraSettingsImpl
import spray.json.JsNumber
import spray.json.JsObject

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.reflect.ClassTag

/**
 * Convenience methods for Knora routes.
 */
object RouteUtilV1 {

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as JSON.
   *
   * @param requestMessage   a [[KnoraRequestV1]] message that should be sent to the responder manager.
   * @param requestContext   the akka-http [[RequestContext]].
   * @param settings         the application's settings.
   * @param responderManager a reference to the responder manager.
   * @param log              a logging adapter.
   * @param timeout          a timeout for `ask` messages.
   * @param executionContext an execution context for futures.
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runJsonRoute(
    requestMessage: KnoraRequestV1,
    requestContext: RequestContext,
    settings: KnoraSettingsImpl,
    responderManager: ActorRef,
    log: LoggingAdapter
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {
    // Optionally log the request message. TODO: move this to the testing framework.
    if (settings.dumpMessages) {
      log.debug(requestMessage.toString)
    }

    val httpResponse: Future[HttpResponse] = for {
      // Make sure the responder sent a reply of type KnoraResponseV1.
      knoraResponse <- (responderManager ? requestMessage).map {
                         case replyMessage: KnoraResponseV1 => replyMessage

                         case other =>
                           // The responder returned an unexpected message type (not an exception). This isn't the client's
                           // fault, so log it and return an error message to the client.
                           throw UnexpectedMessageException(
                             s"Responder sent a reply of type ${other.getClass.getCanonicalName}"
                           )
                       }

      // Optionally log the reply message. TODO: move this to the testing framework.
      _ = if (settings.dumpMessages) {
            log.debug(knoraResponse.toString)
          }

      // The request was successful, so add a status of ApiStatusCodesV1.OK to the response.
      jsonResponseWithStatus =
        JsObject(
          knoraResponse.toJsValue.asJsObject.fields + ("status" -> JsNumber(ApiStatusCodesV1.OK.id))
        )

    } yield HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        jsonResponseWithStatus.compactPrint
      )
    )

    requestContext.complete(httpResponse)
  }

  /**
   * Sends a message (resulting from a [[Future]]) to a responder and completes the HTTP request by returning the response as JSON.
   *
   * @param requestMessageF  a [[Future]] containing a [[KnoraRequestV1]] message that should be sent to the responder manager.
   * @param requestContext   the akka-http [[RequestContext]].
   * @param settings         the application's settings.
   * @param responderManager a reference to the responder manager.
   * @param log              a logging adapter.
   * @param timeout          a timeout for `ask` messages.
   * @param executionContext an execution context for futures.
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runJsonRouteWithFuture[RequestMessageT <: KnoraRequestV1](
    requestMessageF: Future[RequestMessageT],
    requestContext: RequestContext,
    settings: KnoraSettingsImpl,
    responderManager: ActorRef,
    log: LoggingAdapter
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] =
    for {
      requestMessage <- requestMessageF
      routeResult <- runJsonRoute(
                       requestMessage = requestMessage,
                       requestContext = requestContext,
                       settings = settings,
                       responderManager = responderManager,
                       log = log
                     )
    } yield routeResult

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as HTML.
   *
   * @tparam RequestMessageT the type of request message to be sent to the responder.
   * @tparam ReplyMessageT   the type of reply message expected from the responder.
   * @param requestMessageF  a [[Future]] containing the message that should be sent to the responder manager.
   * @param viewHandler      a function that can generate HTML from the responder's reply message.
   * @param requestContext   the [[RequestContext]].
   * @param settings         the application's settings.
   * @param responderManager a reference to the responder manager.
   * @param log              a logging adapter.
   * @param timeout          a timeout for `ask` messages.
   * @param executionContext an execution context for futures.
   */
  def runHtmlRoute[RequestMessageT <: KnoraRequestV1, ReplyMessageT <: KnoraResponseV1: ClassTag](
    requestMessageF: Future[RequestMessageT],
    viewHandler: (ReplyMessageT, ActorRef) => String,
    requestContext: RequestContext,
    settings: KnoraSettingsImpl,
    responderManager: ActorRef,
    log: LoggingAdapter
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[RouteResult] = {

    val httpResponse: Future[HttpResponse] = for {

      requestMessage <- requestMessageF

      // Optionally log the request message. TODO: move this to the testing framework.
      _ = if (settings.dumpMessages) {
            log.debug(requestMessage.toString)
          }

      // Make sure the responder sent a reply of type ReplyMessageT.
      knoraResponse <- (responderManager ? requestMessage).map {
                         case replyMessage: ReplyMessageT => replyMessage

                         case other =>
                           // The responder returned an unexpected message type. This isn't the client's fault, so
                           // log the error and notify the client.
                           val msg = s"Responder sent a reply of type ${other.getClass.getCanonicalName}"
                           throw UnexpectedMessageException(msg)
                       }

      // Optionally log the reply message. TODO: move this to the testing framework.
      _ = if (settings.dumpMessages) {
            log.debug(knoraResponse.toString)
          }

    } yield HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        viewHandler(knoraResponse, responderManager)
      )
    )

    requestContext.complete(httpResponse)
  }

  /**
   * Converts XML to a [[TextWithStandoffTagsV2]], representing the text and its standoff markup.
   *
   * @param xml                            the given XML to be converted to standoff.
   * @param mappingIri                     the mapping to be used to convert the XML to standoff.
   * @param acceptStandoffLinksToClientIDs if `true`, allow standoff link tags to use the client's IDs for target
   *                                       resources. In a bulk import, this allows standoff links to resources
   *                                       that are to be created by the import.
   * @param userProfile                    the user making the request.
   * @param featureFactoryConfig           the feature factory configuration.
   * @param settings                       the application's settings.
   * @param responderManager               a reference to the responder manager.
   * @param log                            a logging adapter.
   * @param timeout                        a timeout for `ask` messages.
   * @param executionContext               an execution context for futures.
   * @return a [[TextWithStandoffTagsV2]].
   */
  def convertXMLtoStandoffTagV1(
    xml: String,
    mappingIri: IRI,
    acceptStandoffLinksToClientIDs: Boolean,
    userProfile: UserADM,
    featureFactoryConfig: FeatureFactoryConfig,
    settings: KnoraSettingsImpl,
    responderManager: ActorRef,
    log: LoggingAdapter
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[TextWithStandoffTagsV2] =
    for {

      // get the mapping directly from v2 responder directly (to avoid useless back and forth conversions between v2 and v1 message formats)
      mappingResponse: GetMappingResponseV2 <- (responderManager ? GetMappingRequestV2(
                                                 mappingIri = mappingIri,
                                                 featureFactoryConfig = featureFactoryConfig,
                                                 requestingUser = userProfile
                                               )).mapTo[GetMappingResponseV2]

      textWithStandoffTagV1 = StandoffTagUtilV2.convertXMLtoStandoffTagV2(
                                xml = xml,
                                mapping = mappingResponse,
                                acceptStandoffLinksToClientIDs = acceptStandoffLinksToClientIDs,
                                log = log
                              )

    } yield textWithStandoffTagV1

  /**
   * MIME types used in Sipi to store image files.
   */
  private val imageMimeTypes: Set[String] = Set(
    "image/jp2",
    "image/jpx"
  )

  /**
   * MIME types used in Sipi to store text files.
   */
  private val textMimeTypes: Set[String] = Set(
    "application/xml",
    "text/xml",
    "text/csv",
    "text/plain"
  )

  /**
   * MIME types used in Sipi to store document files.
   */
  private val documentMimeTypes: Set[String] = Set(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
  )

  /**
   * MIME types used in Sipi to store audio files.
   */
  private val audioMimeTypes: Set[String] = Set(
    "audio/mpeg",
    "audio/mp4",
    "audio/wav",
    "audio/x-wav",
    "audio/vnd.wave"
  )

  /**
   * MIME types used in Sipi to store video files.
   */
  private val videoMimeTypes: Set[String] = Set(
    "video/mp4"
  )

  /**
   * MIME types used in Sipi to store archive files.
   */
  private val archiveMimeTypes: Set[String] = Set(
    "application/zip",
    "application/x-tar",
    "application/gzip",
    "application/x-7z-compressed"
  )

  /**
   * Converts file metadata from Sipi into a [[FileValueV1]].
   *
   * @param filename             the filename.
   * @param fileMetadataResponse the file metadata from Sipi.
   * @param projectShortcode     the project short code that the file value is to be created in.
   * @return a [[FileValueV1]] representing the file.
   */
  def makeFileValue(
    filename: String,
    fileMetadataResponse: GetFileMetadataResponse,
    projectShortcode: String
  ): FileValueV1 =
    if (imageMimeTypes.contains(fileMetadataResponse.internalMimeType)) {
      StillImageFileValueV1(
        internalFilename = filename,
        internalMimeType = fileMetadataResponse.internalMimeType,
        originalFilename = fileMetadataResponse.originalFilename,
        originalMimeType = fileMetadataResponse.originalMimeType,
        projectShortcode = projectShortcode,
        dimX = fileMetadataResponse.width.getOrElse(throw SipiException(s"Sipi did not return the width of the image")),
        dimY =
          fileMetadataResponse.height.getOrElse(throw SipiException(s"Sipi did not return the height of the image"))
      )
    } else if (textMimeTypes.contains(fileMetadataResponse.internalMimeType)) {
      TextFileValueV1(
        internalFilename = filename,
        internalMimeType = fileMetadataResponse.internalMimeType,
        originalFilename = fileMetadataResponse.originalFilename,
        originalMimeType = fileMetadataResponse.originalMimeType,
        projectShortcode = projectShortcode
      )
    } else if (documentMimeTypes.contains(fileMetadataResponse.internalMimeType)) {
      DocumentFileValueV1(
        internalFilename = filename,
        internalMimeType = fileMetadataResponse.internalMimeType,
        originalFilename = fileMetadataResponse.originalFilename,
        originalMimeType = fileMetadataResponse.originalMimeType,
        projectShortcode = projectShortcode,
        pageCount = fileMetadataResponse.pageCount,
        dimX = fileMetadataResponse.width,
        dimY = fileMetadataResponse.height
      )
    } else if (audioMimeTypes.contains(fileMetadataResponse.internalMimeType)) {
      AudioFileValueV1(
        internalFilename = filename,
        internalMimeType = fileMetadataResponse.internalMimeType,
        originalFilename = fileMetadataResponse.originalFilename,
        originalMimeType = fileMetadataResponse.originalMimeType,
        projectShortcode = projectShortcode,
        duration = fileMetadataResponse.duration
      )
    } else if (videoMimeTypes.contains(fileMetadataResponse.internalMimeType)) {
      MovingImageFileValueV1(
        internalFilename = filename,
        internalMimeType = fileMetadataResponse.internalMimeType,
        originalFilename = fileMetadataResponse.originalFilename,
        originalMimeType = fileMetadataResponse.originalMimeType,
        projectShortcode = projectShortcode,
        duration = fileMetadataResponse.duration,
        fps = fileMetadataResponse.fps,
        dimX = fileMetadataResponse.width.getOrElse(throw SipiException(s"Sipi did not return the width of the video")),
        dimY =
          fileMetadataResponse.height.getOrElse(throw SipiException(s"Sipi did not return the height of the video"))
      )
    } else if (archiveMimeTypes.contains(fileMetadataResponse.internalMimeType)) {
      ArchiveFileValueV1(
        internalFilename = filename,
        internalMimeType = fileMetadataResponse.internalMimeType,
        originalFilename = fileMetadataResponse.originalFilename,
        originalMimeType = fileMetadataResponse.originalMimeType,
        projectShortcode = projectShortcode
      )
    } else {
      throw BadRequestException(s"MIME type ${fileMetadataResponse.internalMimeType} not supported in Knora API v1")
    }
}
