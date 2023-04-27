/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.ContentTypes.`text/html(UTF-8)`
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.RouteResult
import akka.util.ByteString
import spray.json.JsNumber
import spray.json.JsObject
import zio._

import java.time.Instant
import scala.concurrent.Future

import dsp.errors.BadRequestException
import org.knora.webapi.IRI
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.http.status.ApiStatusCodesV1
import org.knora.webapi.messages.ResponderRequest.KnoraRequestV1
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.GetFileMetadataResponse
import org.knora.webapi.messages.twirl.ResourceHtmlView
import org.knora.webapi.messages.util.standoff.StandoffStringUtil
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2.TextWithStandoffTagsV2
import org.knora.webapi.messages.v1.responder.KnoraResponseV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceFullResponseV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourcesResponderRequestV1
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffTagV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.store.iiif.errors.SipiException

/**
 * Convenience methods for Knora routes.
 */
object RouteUtilV1 {

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as JSON.
   *
   * @param requestMessage   a  [[KnoraRequestV1]] message that should be sent to the responder manager.
   * @param requestContext   the akka-http [[RequestContext]].
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runJsonRoute(requestMessage: KnoraRequestV1, requestContext: RequestContext)(implicit
    runtime: Runtime[MessageRelay]
  ): Future[RouteResult] =
    UnsafeZioRun.runToFuture(doRunJsonRoute(requestMessage, requestContext))

  private def doRunJsonRoute(request: KnoraRequestV1, ctx: RequestContext): ZIO[MessageRelay, Throwable, RouteResult] =
    createResponse(request).flatMap(completeContext(ctx, _))

  private def createResponse(request: KnoraRequestV1): ZIO[MessageRelay, Throwable, HttpResponse] =
    for {
      knoraResponse <- MessageRelay.ask[KnoraResponseV1](request)
      jsonBody       = JsObject(knoraResponse.toJsValue.asJsObject.fields + ("status" -> JsNumber(ApiStatusCodesV1.OK.id)))
    } yield okResponse(`application/json`, jsonBody.compactPrint)

  private def okResponse(contentType: ContentType, body: String) =
    HttpResponse(StatusCodes.OK, entity = HttpEntity(contentType, ByteString(body)))

  private def completeContext(ctx: RequestContext, response: HttpResponse): Task[RouteResult] =
    ZIO.fromFuture(_ => ctx.complete(response))

  /**
   * Sends a message (resulting from a [[Future]]) to a responder and completes the HTTP request by returning the response as JSON.
   *
   * @param requestFuture    A [[Future]] containing a [[KnoraRequestV1]] message that should be sent to the responder manager.
   * @param requestContext   The akka-http [[RequestContext]].
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runJsonRouteF[RequestMessageT <: KnoraRequestV1](
    requestFuture: Future[RequestMessageT],
    requestContext: RequestContext
  )(implicit runtime: Runtime[MessageRelay]): Future[RouteResult] =
    UnsafeZioRun.runToFuture(ZIO.fromFuture(_ => requestFuture).flatMap(doRunJsonRoute(_, requestContext)))

  /**
   * Sends a message (resulting from a [[Future]]) to a responder and completes the HTTP request by returning the response as JSON.
   *
   * @param requestTask    A [[Task]] containing a [[KnoraRequestV1]] message that should be sent to the responder manager.
   * @param requestContext The akka-http [[RequestContext]].
   * @return a [[Future]] containing a [[RouteResult]].
   */
  def runJsonRouteZ[R](requestTask: ZIO[R, Throwable, KnoraRequestV1], requestContext: RequestContext)(implicit
    runtime: Runtime[R with MessageRelay]
  ): Future[RouteResult] =
    UnsafeZioRun.runToFuture(requestTask.flatMap(doRunJsonRoute(_, requestContext)))

  /**
   * Sends a message to a responder and completes the HTTP request by returning the response as HTML.
   *
   * @param requestTask      A [[Task]] containing the message that should be sent to the responder manager.
   * @param requestContext   The [[RequestContext]].
   */
  def runHtmlRoute[R](requestTask: ZIO[R, Throwable, ResourcesResponderRequestV1], requestContext: RequestContext)(
    implicit runtime: Runtime[R with MessageRelay]
  ): Future[RouteResult] =
    UnsafeZioRun.runToFuture(for {
      requestMessage <- requestTask
      knoraResponse  <- MessageRelay.ask[ResourceFullResponseV1](requestMessage)
      html           <- ResourceHtmlView.propertiesHtmlView(knoraResponse)
      result         <- completeContext(requestContext, okResponse(`text/html(UTF-8)`, html))
    } yield result)

  /**
   * Converts XML to a [[TextWithStandoffTagsV2]], representing the text and its standoff markup.
   *
   * @param xml                            the given XML to be converted to standoff.
   * @param mappingIri                     the mapping to be used to convert the XML to standoff.
   * @param acceptStandoffLinksToClientIDs if `true`, allow standoff link tags to use the client's IDs for target
   *                                       resources. In a bulk import, this allows standoff links to resources
   *                                       that are to be created by the import.
   * @param userProfile                    the user making the request.
   * @return a [[TextWithStandoffTagsV2]].
   */
  def convertXMLtoStandoffTagV1(
    xml: String,
    mappingIri: IRI,
    acceptStandoffLinksToClientIDs: Boolean,
    userProfile: UserADM
  ): ZIO[MessageRelay, Throwable, TextWithStandoffTagsV2] =
    for {
      mappingResponse <- MessageRelay.ask[GetMappingResponseV2](GetMappingRequestV2(mappingIri, userProfile))
      textWithStandoffTag <-
        ZIO.attempt(
          StandoffTagUtilV2.convertXMLtoStandoffTagV2(xml, mappingResponse, acceptStandoffLinksToClientIDs)
        )
    } yield textWithStandoffTag

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
  ): Task[FileValueV1] =
    if (imageMimeTypes.contains(fileMetadataResponse.internalMimeType)) {
      for {
        dimX <- ZIO
                  .fromOption(fileMetadataResponse.width)
                  .orElseFail(SipiException(s"Sipi did not return the width of the image"))
        dimY <- ZIO
                  .fromOption(fileMetadataResponse.height)
                  .orElseFail(SipiException(s"Sipi did not return the height of the image"))
      } yield StillImageFileValueV1(
        internalFilename = filename,
        internalMimeType = fileMetadataResponse.internalMimeType,
        originalFilename = fileMetadataResponse.originalFilename,
        originalMimeType = fileMetadataResponse.originalMimeType,
        projectShortcode = projectShortcode,
        dimX = dimX,
        dimY = dimY
      )
    } else if (textMimeTypes.contains(fileMetadataResponse.internalMimeType)) {
      ZIO.succeed(
        TextFileValueV1(
          internalFilename = filename,
          internalMimeType = fileMetadataResponse.internalMimeType,
          originalFilename = fileMetadataResponse.originalFilename,
          originalMimeType = fileMetadataResponse.originalMimeType,
          projectShortcode = projectShortcode
        )
      )
    } else if (documentMimeTypes.contains(fileMetadataResponse.internalMimeType)) {
      ZIO.succeed(
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
      )
    } else if (audioMimeTypes.contains(fileMetadataResponse.internalMimeType)) {
      ZIO.succeed(
        AudioFileValueV1(
          internalFilename = filename,
          internalMimeType = fileMetadataResponse.internalMimeType,
          originalFilename = fileMetadataResponse.originalFilename,
          originalMimeType = fileMetadataResponse.originalMimeType,
          projectShortcode = projectShortcode,
          duration = fileMetadataResponse.duration
        )
      )
    } else if (videoMimeTypes.contains(fileMetadataResponse.internalMimeType)) {
      for {
        dimX <- ZIO
                  .fromOption(fileMetadataResponse.width)
                  .orElseFail(SipiException(s"Sipi did not return the width of the video"))
        dimY <- ZIO
                  .fromOption(fileMetadataResponse.height)
                  .orElseFail(SipiException(s"Sipi did not return the height of the video"))
      } yield MovingImageFileValueV1(
        internalFilename = filename,
        internalMimeType = fileMetadataResponse.internalMimeType,
        originalFilename = fileMetadataResponse.originalFilename,
        originalMimeType = fileMetadataResponse.originalMimeType,
        projectShortcode = projectShortcode,
        duration = fileMetadataResponse.duration,
        fps = fileMetadataResponse.fps,
        dimX = dimX,
        dimY = dimY
      )
    } else if (archiveMimeTypes.contains(fileMetadataResponse.internalMimeType)) {
      ZIO.succeed(
        ArchiveFileValueV1(
          internalFilename = filename,
          internalMimeType = fileMetadataResponse.internalMimeType,
          originalFilename = fileMetadataResponse.originalFilename,
          originalMimeType = fileMetadataResponse.originalMimeType,
          projectShortcode = projectShortcode
        )
      )
    } else {
      ZIO.fail(BadRequestException(s"MIME type ${fileMetadataResponse.internalMimeType} not supported in Knora API v1"))
    }

  def verifyNumberOfParams[A](
    params: Seq[A],
    errorMessage: String,
    length: Int
  ): IO[BadRequestException, Option[Nothing]] =
    ZIO.fail(BadRequestException(errorMessage)).when(params.length != length)

  def toSparqlEncodedString(str: String, errorMsg: String): ZIO[StringFormatter, BadRequestException, IRI] =
    ZIO.fromOption(StringFormatter.toSparqlEncodedString(str)).orElseFail(BadRequestException(errorMsg))

  def getResourceIrisFromStandoffTags(tags: Seq[StandoffTagV2]): Task[Set[IRI]] =
    ZIO.attempt(StandoffStringUtil.getResourceIrisFromStandoffTags(tags))

  def xsdDateTimeStampToInstant(s: String, errorMsg: String): IO[Throwable, Instant] =
    ZIO.fromOption(ValuesValidator.xsdDateTimeStampToInstant(s)).orElseFail(BadRequestException(errorMsg))

  def getUserProfileV1(ctx: RequestContext): ZIO[Authenticator, Throwable, UserProfileV1] =
    Authenticator.getUserADM(ctx).map(_.asUserProfileV1)

  def getProjectByIri(projectIri: String): ZIO[KnoraProjectRepo, BadRequestException, KnoraProject] =
    for {
      projectId <- IriIdentifier
                     .fromString(projectIri)
                     .toZIO
                     .orElseFail(BadRequestException(s"Invalid project IRI: $projectIri"))
      project <- ZIO
                   .serviceWithZIO[KnoraProjectRepo](_.findById(projectId))
                   .flatMap(ZIO.fromOption(_))
                   .orElseFail(BadRequestException(s"Project '$projectIri' not found"))
    } yield project
}
