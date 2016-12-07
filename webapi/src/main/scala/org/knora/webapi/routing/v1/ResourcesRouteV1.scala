/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */


package org.knora.webapi.routing.v1

import java.io.File
import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.FileInfo
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import com.typesafe.scalalogging.Logger
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceV1JsonProtocol._
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.sipimessages.{SipiResponderConversionFileRequestV1, SipiResponderConversionPathRequestV1}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.InputValidation.RichtextComponents
import org.knora.webapi.util.{DateUtilV1, InputValidation}
import org.knora.webapi.viewhandlers.ResourceHtmlView
import org.slf4j.LoggerFactory
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Provides a spray-routing function for API routes that deal with resources.
  */
object ResourcesRouteV1 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, loggingAdapter: LoggingAdapter): Route = {

        implicit val system: ActorSystem = _system
        implicit val materializer = ActorMaterializer()
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        val log = Logger(LoggerFactory.getLogger(this.getClass))

        def makeResourceRequestMessage(resIri: String,
                                       resinfo: Boolean,
                                       requestType: String,
                                       userProfile: UserProfileV1): ResourcesResponderRequestV1 = {
            val validResIri = InputValidation.toIri(resIri, () => throw BadRequestException(s"Invalid resource IRI: $resIri"))

            requestType match {
                case "info" => ResourceInfoGetRequestV1(iri = validResIri, userProfile = userProfile)
                case "rights" => ResourceRightsGetRequestV1(validResIri, userProfile)
                case "context" => ResourceContextGetRequestV1(validResIri, userProfile, resinfo)
                case "" => ResourceFullGetRequestV1(validResIri, userProfile)
                case other => throw BadRequestException(s"Invalid request type: $other")
            }
        }

        def makeResourceSearchRequestMessage(searchString: String,
                                             resourceTypeIri: Option[IRI],
                                             numberOfProps: Int, limitOfResults: Int,
                                             userProfile: UserProfileV1): ResourceSearchGetRequestV1 = {
            ResourceSearchGetRequestV1(searchString = searchString, resourceTypeIri = resourceTypeIri, numberOfProps = numberOfProps, limitOfResults = limitOfResults, userProfile = userProfile)
        }

        def makeCreateResourceRequestMessage(apiRequest: CreateResourceApiRequestV1, multipartConversionRequest: Option[SipiResponderConversionPathRequestV1] = None, userProfile: UserProfileV1): ResourceCreateRequestV1 = {

            val projectIri = InputValidation.toIri(apiRequest.project_id, () => throw BadRequestException(s"Invalid project IRI: ${apiRequest.project_id}"))
            val resourceTypeIri = InputValidation.toIri(apiRequest.restype_id, () => throw BadRequestException(s"Invalid resource IRI: ${apiRequest.restype_id}"))
            val label = InputValidation.toSparqlEncodedString(apiRequest.label, () => throw BadRequestException(s"Invalid label: '${apiRequest.label}'"))

            // for GUI-case:
            // file has already been stored by Sipi.
            // TODO: in the old SALSAH, the file params were sent as a property salsah:__location__ -> the GUI has to be adapated
            val paramConversionRequest: Option[SipiResponderConversionFileRequestV1] = apiRequest.file match {
                case Some(createFile: CreateFileV1) => Some(SipiResponderConversionFileRequestV1(
                    originalFilename = InputValidation.toSparqlEncodedString(createFile.originalFilename, () => throw BadRequestException(s"The original filename is invalid: '${createFile.originalFilename}'")),
                    originalMimeType = InputValidation.toSparqlEncodedString(createFile.originalMimeType, () => throw BadRequestException(s"The original MIME type is invalid: '${createFile.originalMimeType}'")),
                    filename = InputValidation.toSparqlEncodedString(createFile.filename, () => throw BadRequestException(s"Invalid filename: '${createFile.filename}'")),
                    userProfile = userProfile
                ))
                case None => None
            }

            val valuesToBeCreated: Map[IRI, Seq[CreateValueV1WithComment]] = apiRequest.properties.map {
                case (propIri: IRI, values: Seq[CreateResourceValueV1]) =>
                    (InputValidation.toIri(propIri, () => throw BadRequestException(s"Invalid property IRI $propIri")), values.map {
                        case (givenValue: CreateResourceValueV1) =>
                            // TODO: These match-case statements with lots of underlines are ugly and confusing.

                            givenValue match {
                                // create corresponding UpdateValueV1

                                case CreateResourceValueV1(Some(richtext: CreateRichtextV1), _, _, _, _, _, _, _, _, _, _, _, comment) =>

                                    val richtextComponents: RichtextComponents = InputValidation.handleRichtext(richtext)

                                    CreateValueV1WithComment(TextValueV1(InputValidation.toSparqlEncodedString(richtext.utf8str, () => throw BadRequestException(s"Invalid text: '${richtext.utf8str}'")),
                                        textattr = richtextComponents.textattr,
                                        resource_reference = richtextComponents.resource_reference),
                                        comment)

                                case CreateResourceValueV1(_, Some(linkValue: IRI), _, _, _, _, _, _, _, _, _, _, comment) =>
                                    val linkVal = InputValidation.toIri(linkValue, () => throw BadRequestException(s"Invalid Knora resource IRI: $linkValue"))
                                    CreateValueV1WithComment(LinkUpdateV1(linkVal), comment)

                                case CreateResourceValueV1(_, _, Some(intValue: Int), _, _, _, _, _, _, _, _, _, comment) => CreateValueV1WithComment(IntegerValueV1(intValue), comment)

                                case CreateResourceValueV1(_, _, _, Some(decimalValue: BigDecimal), _, _, _, _, _, _, _, _, comment) =>
                                    CreateValueV1WithComment(DecimalValueV1(decimalValue), comment)

                                case CreateResourceValueV1(_, _, _, _, Some(booleanValue: Boolean), _, _, _, _, _, _, _, comment) =>
                                    CreateValueV1WithComment(BooleanValueV1(booleanValue), comment)

                                case CreateResourceValueV1(_, _, _, _, _, Some(uriValue: String), _, _, _, _, _, _, comment) =>
                                    CreateValueV1WithComment(UriValueV1(InputValidation.toIri(uriValue, () => throw BadRequestException(s"Invalid URI: $uriValue"))), comment)

                                case CreateResourceValueV1(_, _, _, _, _, _, Some(dateStr: String), _, _, _, _, _, comment) =>
                                    CreateValueV1WithComment(DateUtilV1.createJDNValueV1FromDateString(dateStr), comment)

                                case CreateResourceValueV1(_, _, _, _, _, _, _, Some(colorStr: String), _, _, _, _, comment) =>
                                    val colorValue = InputValidation.toColor(colorStr, () => throw BadRequestException(s"Invalid color value: $colorStr"))
                                    CreateValueV1WithComment(ColorValueV1(colorValue), comment)

                                case CreateResourceValueV1(_, _, _, _, _, _, _, _, Some(geomStr: String), _, _, _, comment) =>
                                    val geometryValue = InputValidation.toGeometryString(geomStr, () => throw BadRequestException(s"Invalid geometry value: $geomStr"))
                                    CreateValueV1WithComment(GeomValueV1(geometryValue), comment)

                                case CreateResourceValueV1(_, _, _, _, _, _, _, _, _, Some(hlistValue: IRI), _, _, comment) =>
                                    val listNodeIri = InputValidation.toIri(hlistValue, () => throw BadRequestException(s"Invalid value IRI: $hlistValue"))
                                    CreateValueV1WithComment(HierarchicalListValueV1(listNodeIri), comment)

                                case CreateResourceValueV1(_, _, _, _, _, _, _, _, _, _, Some(Seq(timeval1: BigDecimal, timeval2: BigDecimal)), _, comment) =>
                                    CreateValueV1WithComment(IntervalValueV1(timeval1, timeval2), comment)

                                case CreateResourceValueV1(_, _, _, _, _, _, _, _, _, _, _, Some(geonameStr: String), comment) =>
                                    CreateValueV1WithComment(GeonameValueV1(geonameStr), comment)

                                case _ => throw BadRequestException(s"No value submitted")

                            }

                    })
            }

            // since this function `makeCreateResourceRequestMessage` is called by the POST multipart route receiving the binaries (non GUI-case)
            // and by the other POST route, either multipartConversionRequest or paramConversionRequest is set if a file should be attached to the resource, but not both.
            if (multipartConversionRequest.nonEmpty && paramConversionRequest.nonEmpty) throw BadRequestException("Binaries sent and file params set to route. This is illegal.")

            ResourceCreateRequestV1(
                resourceTypeIri = resourceTypeIri,
                label = label,
                projectIri = projectIri,
                values = valuesToBeCreated,
                file = if (multipartConversionRequest.nonEmpty) // either multipartConversionRequest or paramConversionRequest might be given, but never both
                    multipartConversionRequest // Non GUI-case
                else if (paramConversionRequest.nonEmpty)
                    paramConversionRequest // GUI-case
                else None, // no file given
                userProfile = userProfile,
                apiRequestID = UUID.randomUUID
            )
        }

        def makeGetPropertiesRequestMessage(resIri: IRI, userProfile: UserProfileV1) = {
            PropertiesGetRequestV1(resIri, userProfile)
        }

        def makeResourceDeleteMessage(resIri: IRI, deleteComment: Option[String], userProfile: UserProfileV1) = {
            ResourceDeleteRequestV1(
                resourceIri = InputValidation.toIri(resIri, () => throw BadRequestException(s"Invalid resource IRI: $resIri")),
                deleteComment = deleteComment.map(comment => InputValidation.toSparqlEncodedString(comment, () => throw BadRequestException(s"Invalid comment: '$comment'"))),
                userProfile = userProfile,
                apiRequestID = UUID.randomUUID
            )
        }

        path("v1" / "resources") {
            get {
                // search for resources matching the given search string (searchstr) and return their Iris.
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val params = requestContext.request.uri.query().toMap
                    val searchstr = params.getOrElse("searchstr", throw BadRequestException(s"required param searchstr is missing"))
                    val restype = params.getOrElse("restype_id", "-1") // default -1 means: no restriction at all
                    val numprops = params.getOrElse("numprops", "1")
                    val limit = params.getOrElse("limit", "11")

                    // input validation

                    val searchString = InputValidation.toSparqlEncodedString(searchstr, () => throw BadRequestException(s"Invalid search string: '$searchstr'"))

                    val resourceTypeIri: Option[IRI] = restype match {
                        case ("-1") => None
                        case (restype: IRI) => Some(InputValidation.toIri(restype, () => throw BadRequestException(s"Invalid param restype: $restype")))
                    }

                    val numberOfProps: Int = InputValidation.toInt(numprops, () => throw BadRequestException(s"Invalid param numprops: $numprops")) match {
                        case (number: Int) => if (number < 1) 1 else number // numberOfProps must not be smaller than 1
                    }

                    val limitOfResults = InputValidation.toInt(limit, () => throw BadRequestException(s"Invalid param limit: $limit"))

                    val requestMessage = makeResourceSearchRequestMessage(
                        searchString = searchString,
                        resourceTypeIri = resourceTypeIri,
                        numberOfProps = numberOfProps,
                        limitOfResults = limitOfResults,
                        userProfile = userProfile
                    )

                    RouteUtilV1.runJsonRoute(
                        Future(requestMessage),
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
            } ~ post {
                // Create a new resource with he given type and possibly a file (GUI-case).
                // The binary file is already managed by Sipi.
                // For further details, please read the docs: Sipi -> Interaction Between Sipi and Knora.
                entity(as[CreateResourceApiRequestV1]) { apiRequest => requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val requestMessage = makeCreateResourceRequestMessage(apiRequest = apiRequest, userProfile = userProfile)

                    RouteUtilV1.runJsonRoute(
                        Future(requestMessage),
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            } ~ post {
                // Create a new resource with the given type, properties, and binary data (file) (non GUI-case).
                // The binary data are contained in the request and have to be temporarily stored by Knora.
                // For further details, please read the docs: Sipi -> Interaction Between Sipi and Knora.
                entity(as[Multipart.FormData]) { formdata: Multipart.FormData => requestContext =>

                    log.debug("/v1/resources - POST - Multipart.FormData - Route")

                    val userProfile = getUserProfileV1(requestContext)

                    type Name = String

                    val JSON_PART = "json"
                    val FILE_PART = "file"

                    /* TODO: refactor to remove the need for this var */
                    /* makes sure that variables updated in another thread return the latest version */
                    @volatile var receivedFile: Option[File] = None

                    log.debug(s"receivedFile is defined before: ${receivedFile.isDefined}")

                    // collect all parts of the multipart as it arrives into a map
                    val allPartsFuture: Future[Map[Name, Any]] = formdata.parts.mapAsync[(Name, Any)](1) {
                            case b: BodyPart if b.name == JSON_PART => {
                                log.debug(s"inside allPartsFuture - processing $JSON_PART")
                                b.toStrict(2.seconds).map(strict => (b.name, strict.entity.data.utf8String.parseJson))
                            }
                            case b: BodyPart if b.name == FILE_PART => {
                                log.debug(s"inside allPartsFuture - processing $FILE_PART")
                                val filename = b.filename.getOrElse(throw BadRequestException(s"Filename is not given"))
                                val tmpFile = InputValidation.createTempFile(settings)
                                val written = b.entity.dataBytes.runWith(FileIO.toPath(tmpFile.toPath))
                                written.map { written =>
                                    //println(s"written result: ${written.wasSuccessful}, ${b.filename.get}, ${tmpFile.getAbsolutePath}")
                                    receivedFile = Some(tmpFile)
                                    (b.name, FileInfo(b.name, b.filename.get, b.entity.contentType))
                                }
                            }
                            case b: BodyPart if b.name.isEmpty => throw BadRequestException("part of HTTP multipart request has no name")
                            case b: BodyPart => throw BadRequestException(s"multipart contains invalid name: ${b.name}")
                    }.runFold(Map.empty[Name, Any])((map, tuple) => map + tuple)

                    // this file will be deleted by Knora once it is not needed anymore
                    // TODO: add a script that cleans files in the tmp location that have a certain age
                    // TODO  (in case they were not deleted by Knora which should not happen -> this has also to be implemented for Sipi for the thumbnails)
                    // TODO: how to check if the user has sent multiple files?

                    val requestMessageFuture: Future[ResourceCreateRequestV1] = allPartsFuture.map { allParts =>

                        log.debug(s"allParts: $allParts")
                        log.debug(s"receivedFile is defined: ${receivedFile.isDefined}")

                        // get the json params and turn them into a case class
                        val apiRequest: CreateResourceApiRequestV1 = try {
                            allParts.getOrElse(JSON_PART, throw BadRequestException(s"MultiPart POST request was sent without required '$JSON_PART' part!")).asInstanceOf[JsValue].convertTo[CreateResourceApiRequestV1]
                        } catch {
                            case e: DeserializationException => throw BadRequestException("JSON params structure is invalid: " + e.toString)
                        }

                        // check if the API request contains file information: this is illegal for this route
                        if (apiRequest.file.nonEmpty) throw BadRequestException("param 'file' is set for a post multipart request. This is not allowed.")

                        val sourcePath = receivedFile.getOrElse(throw FileUploadException())

                        // get the file info containing the original filename and content type.
                        val fileInfo = allParts.getOrElse(FILE_PART, throw BadRequestException(s"MultiPart POST request was sent without required '$FILE_PART' part!")).asInstanceOf[FileInfo]
                        val originalFilename = fileInfo.fileName
                        val originalMimeType = fileInfo.contentType.toString


                        val sipiConvertPathRequest = SipiResponderConversionPathRequestV1(
                            originalFilename = InputValidation.toSparqlEncodedString(originalFilename, () => throw BadRequestException(s"Original filename is invalid: '$originalFilename'")),
                            originalMimeType = InputValidation.toSparqlEncodedString(originalMimeType, () => throw BadRequestException(s"Original MIME type is invalid: '$originalMimeType'")),
                            source = sourcePath,
                            userProfile = userProfile
                        )

                        val requestMessage = makeCreateResourceRequestMessage(
                            apiRequest = apiRequest,
                            multipartConversionRequest = Some(sipiConvertPathRequest),
                            userProfile = userProfile
                        )
                        requestMessage
                    }

                    RouteUtilV1.runJsonRoute(
                        requestMessageFuture,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            }
        } ~ path("v1" / "resources" / Segment) { resIri =>
            get {
                parameters("reqtype".?, "resinfo".as[Boolean].?) { (reqtypeParam, resinfoParam) =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)
                        val params = parameterMap
                        val requestType = reqtypeParam.getOrElse("")
                        val resinfo = resinfoParam.getOrElse(false)
                        val requestMessage = makeResourceRequestMessage(resIri = resIri, resinfo = resinfo, requestType = requestType, userProfile = userProfile)

                        RouteUtilV1.runJsonRoute(
                            Future(requestMessage),
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                }
            } ~ delete {
                parameters("deleteComment".?) { deleteCommentParam =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)
                        val requestMessage = makeResourceDeleteMessage(resIri = resIri, deleteComment = deleteCommentParam, userProfile = userProfile)

                        RouteUtilV1.runJsonRoute(
                            Future(requestMessage),
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                }
            }
        } ~ path("v1" / "resources.html" / Segment) { iri =>
            get {
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val params = requestContext.request.uri.query().toMap
                    val requestType = params.getOrElse("reqtype", "")
                    val resIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid param resource IRI: $iri"))

                    val requestMessage = requestType match {
                        case "properties" => ResourceFullGetRequestV1(resIri, userProfile)
                        case other => throw BadRequestException(s"Invalid request type: $other")
                    }

                    RouteUtilV1.runHtmlRoute[ResourcesResponderRequestV1, ResourceFullResponseV1](
                        requestMessage,
                        ResourceHtmlView.propertiesHtmlView,
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
            }
        } ~ path("v1" / "properties" / Segment) { iri =>
            get {
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val resIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid param resource IRI: $iri"))
                    val requestMessage = makeGetPropertiesRequestMessage(resIri, userProfile)

                    RouteUtilV1.runJsonRoute(
                        Future(requestMessage),
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )

            }
        } ~ path("v1" / "resources" / "label" / Segment) { iri =>
            put {
                entity(as[ChangeResourceLabelApiRequestV1]) { apiRequest => requestContext =>
                    val userProfile = getUserProfileV1(requestContext)

                    val resIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid param resource IRI: $iri"))

                    val label = InputValidation.toSparqlEncodedString(apiRequest.label, () => throw BadRequestException(s"Invalid label: '${apiRequest.label}'"))

                    val requestMessage = ChangeResourceLabelRequestV1(
                        resourceIri = resIri,
                        label = label,
                        apiRequestID = UUID.randomUUID,
                        userProfile = userProfile)


                    RouteUtilV1.runJsonRoute(
                        Future(requestMessage),
                        requestContext,
                        settings,
                        responderManager,
                        loggingAdapter
                    )
                }
            }
        } ~ path("v1" / "graphdata" / Segment) { iri =>
            get {
                parameters("depth".as[Int].?) { depth =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)
                        val resourceIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid param resource IRI: $iri"))
                        val requestMessage = GraphDataGetRequestV1(resourceIri, depth.getOrElse(4), userProfile)

                        RouteUtilV1.runJsonRoute(
                            Future(requestMessage),
                            requestContext,
                            settings,
                            responderManager,
                            loggingAdapter
                        )
                }
            }
        }
    }
}