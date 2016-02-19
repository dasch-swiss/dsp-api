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

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.resourcemessages.ResourceV1JsonProtocol._
import org.knora.webapi.messages.v1respondermessages.resourcemessages._
import org.knora.webapi.messages.v1respondermessages.sipimessages.{SipiResponderConversionFileRequestV1, SipiResponderConversionPathRequestV1}
import org.knora.webapi.messages.v1respondermessages.usermessages.UserProfileV1
import org.knora.webapi.messages.v1respondermessages.valuemessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.{DateUtilV1, InputValidation}
import org.knora.webapi.viewhandlers.ResourceHtmlView
import spray.http.HttpEntity.NonEmpty
import spray.http._
import spray.json._
import spray.routing.Directives._
import spray.routing._

import scala.util.Try

/**
 * Provides a spray-routing function for API routes that deal with resources.
 */
object ResourcesRouteV1 extends Authenticator {

    def rapierPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {

        implicit val system: ActorSystem = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        def makeResourceRequestMessage(iri: String,
                                       resinfo: Boolean,
                                       requestType: String,
                                       userProfile: UserProfileV1): ResourcesResponderRequestV1 = {
            requestType match {
                case "info" => ResourceInfoGetRequestV1(iri = iri, userProfile = userProfile)
                case "rights" => ResourceRightsGetRequestV1(iri, userProfile)
                case "context" => ResourceContextGetRequestV1(iri, userProfile, resinfo)
                case "" => ResourceFullGetRequestV1(iri, userProfile)
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
            // necessary import statements to convert to [[StandoffPositionV1]]
            import org.knora.webapi.messages.v1respondermessages.valuemessages.ApiValueV1JsonProtocol._
            import spray.json.JsonParser

            val projectIri = InputValidation.toIri(apiRequest.project_id, () => throw BadRequestException(s"Invalid project IRI ${apiRequest.project_id}"))
            val resourceTypeIri = InputValidation.toIri(apiRequest.restype_id, () => throw BadRequestException(s"Invalid resource IRI ${apiRequest.restype_id}"))
            val label = InputValidation.toSparqlEncodedString(apiRequest.label)

            // for GUI-case:
            // file has already been stored by Sipi.
            // TODO: in the old SALSAH, the file params were sent as a property salsah:__location__ -> the GUI has to be adapated
            val paramConversionRequest: Option[SipiResponderConversionFileRequestV1] = apiRequest.file match {
                case Some(createFile: CreateFileV1) => Some(SipiResponderConversionFileRequestV1(
                    originalFilename = InputValidation.toSparqlEncodedString(createFile.originalFilename),
                    originalMimeType = InputValidation.toSparqlEncodedString(createFile.originalMimeType),
                    filename = InputValidation.toSparqlEncodedString(createFile.filename),
                    userProfile = userProfile
                ))
                case None => None
            }

            val valuesToBeCreated: Map[IRI, Seq[CreateValueV1WithComment]] = apiRequest.properties.map {
                case (propIri: IRI, values: Seq[CreateResourceValueV1]) =>
                    (InputValidation.toIri(propIri, () => throw BadRequestException(s"Invalid property IRI $propIri")), values.map {
                        case (givenValue: CreateResourceValueV1) =>
                            givenValue match {
                                // create corresponding UpdateValueV1
                                case CreateResourceValueV1(_, _, Some(intValue: Int), _, _, _, _, comment) => CreateValueV1WithComment(IntegerValueV1(intValue), comment)
                                case CreateResourceValueV1(Some(richtext: CreateRichtextV1), _, _, _, _, _, _, comment) =>
                                    val textattr: Map[String, Seq[StandoffPositionV1]] = InputValidation.validateTextattr(JsonParser(richtext.textattr).convertTo[Map[String, Seq[StandoffPositionV1]]])
                                    val resourceReference: Seq[IRI] = InputValidation.validateResourceReference(richtext.resource_reference)

                                    CreateValueV1WithComment(TextValueV1(InputValidation.toSparqlEncodedString(richtext.utf8str), textattr = textattr, resource_reference = resourceReference), comment)

                                case CreateResourceValueV1(_, Some(linkValue: IRI), _, _, _, _, _, comment) =>
                                    val linkVal = InputValidation.toIri(linkValue, () => throw BadRequestException(s"Invalid Knora resource Iri $linkValue"))
                                    CreateValueV1WithComment(LinkUpdateV1(linkVal), comment)

                                case CreateResourceValueV1(_, _, _, Some(floatValue: Float), _, _, _, comment) => CreateValueV1WithComment(FloatValueV1(floatValue), comment)

                                case CreateResourceValueV1(_, _, _, _, Some(dateStr: String), _, _, comment) =>
                                    CreateValueV1WithComment(DateUtilV1.createJDCValueV1FromDateString(dateStr), comment)

                                case CreateResourceValueV1(_, _, _, _, _, Some(colorStr: String), _, comment) =>
                                    val colorValue = InputValidation.toColor(colorStr, () => throw BadRequestException(s"Invalid color value $colorStr"))
                                    CreateValueV1WithComment(ColorValueV1(colorValue), comment)

                                case CreateResourceValueV1(_, _, _, _, _, _, Some(geomStr: String), comment) =>
                                    val geometryValue = InputValidation.toGeometryString(geomStr, () => throw BadRequestException(s"Invalid geometry value geomStr"))
                                    CreateValueV1WithComment(GeomValueV1(geometryValue), comment)

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

        path("v1" / "resources" / Segment) { iri =>
            get {
                requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        val params = requestContext.request.uri.query.toMap
                        val requestType = params.getOrElse("reqtype", "")
                        val resinfo: Boolean = params.getOrElse("resinfo", "") == "true"
                        val resIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid param resource IRI: $iri"))
                        makeResourceRequestMessage(resIri, resinfo, requestType, userProfile)
                    }

                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~ path("v1" / "resources") {
            get {
                // search for resources matching the given search string (searchstr) and return their Iris.
                requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        val params = requestContext.request.uri.query.toMap
                        val searchstr = params.getOrElse("searchstr", throw BadRequestException(s"required param searchstr is missing"))
                        val restype = params.getOrElse("restype_id", "-1") // default -1 means: no restriction at all
                        val numprops = params.getOrElse("numprops", "1")
                        val limit = params.getOrElse("limit", "11")

                        // input validation

                        val searchString = InputValidation.toSparqlEncodedString(searchstr)

                        val resourceTypeIri: Option[IRI] = restype match {
                            case ("-1") => None
                            case (restype: IRI) => Some(InputValidation.toIri(restype, () => throw BadRequestException(s"Invalid param restype: $restype")))
                        }

                        val numberOfProps: Int = InputValidation.toInt(numprops, () => throw BadRequestException(s"Invalid param numprops: $numprops")) match {
                            case (number: Int) => if (number < 1) 1 else number // numberOfProps must not be smaller than 1
                        }

                        val limitOfResults = InputValidation.toInt(limit, () => throw BadRequestException(s"Invalid param limit: $limit"))

                        makeResourceSearchRequestMessage(
                            searchString = searchString,
                            resourceTypeIri = resourceTypeIri,
                            numberOfProps = numberOfProps,
                            limitOfResults = limitOfResults,
                            userProfile = userProfile
                        )
                    }

                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            } ~ post {
                entity(as[CreateResourceApiRequestV1]) { apiRequest => requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)

                        makeCreateResourceRequestMessage(apiRequest = apiRequest, userProfile = userProfile)
                    }

                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            } ~ post {
                // create a new resource with the given type, properties, and binary data (file)
                entity(as[MultipartFormData]) { data => requestContext =>

                    val requestMessageTry = Try {

                        // get all the body parts from multipart request
                        val fields: Seq[BodyPart] = data.fields

                        //
                        // turn Sequence of BodyParts into a Map(name -> BodyPart),
                        // according to the given keys in the HTTP request
                        // e.g. 'json' -> BodyPart or 'file' -> BodyPart
                        //
                        val namedParts: Map[String, BodyPart] = fields.map {
                            // assumes that only one file is given (this may change for API V2)
                            case (bodyPart: BodyPart) =>
                                (bodyPart.dispositionParameterValue("name").getOrElse(throw BadRequestException("part of HTTP multipart request has no name")), bodyPart)
                        }.toMap

                        val userProfile = getUserProfileV1(requestContext)

                        // get the json params (access first member of the tuple) and turn them into a case class
                        val apiRequest: CreateResourceApiRequestV1 = try {
                            namedParts.getOrElse("json", throw BadRequestException("Required param 'json' was not submitted"))
                                .entity.asString.parseJson.convertTo[CreateResourceApiRequestV1]
                        } catch {
                            case e: DeserializationException => throw BadRequestException("JSON params structure is invalid: " + e.toString)
                        }

                        // check if the API request contains file information: this is illegal for this route
                        if (apiRequest.file.nonEmpty) throw BadRequestException("param 'file' is set for a post multipart request. This is not allowed.")

                        // get binary data from bodyPart 'file'
                        val bodyPartFile: BodyPart = namedParts.getOrElse("file", throw BadRequestException("MultiPart Post request was sent but no files"))

                        // TODO: how to check if the user has sent multiple files?
                        val nonEmpty: NonEmpty = bodyPartFile.entity
                            .toOption.getOrElse(throw BadRequestException("no binary data submitted in multipart request"))

                        // save file to temporary location
                        // this file will be deleted by Knora once it is not needed anymore
                        // TODO: add a script that cleans files in the tmp location that have a certain age
                        // TODO  (in case they were not deleted by Knora which should not happen -> this has also to be implemented for Sipi for the thumbnails)
                        val sourcePath = InputValidation.saveFileToTmpLocation(settings, nonEmpty.data.toByteArray)

                        val sipiConvertPathRequest = SipiResponderConversionPathRequestV1(
                            originalFilename = InputValidation.toSparqlEncodedString(bodyPartFile.filename.getOrElse(throw BadRequestException(s"Filename is not given"))),
                            originalMimeType = InputValidation.toSparqlEncodedString(nonEmpty.contentType.toString),
                            source = sourcePath,
                            userProfile = userProfile
                        )

                        makeCreateResourceRequestMessage(
                            apiRequest = apiRequest,
                            multipartConversionRequest = Some(sipiConvertPathRequest),
                            userProfile = userProfile
                        )
                    }

                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )

                }
            }
        } ~ path("v1" / "resources.html" / Segment) { iri =>
            get {
                requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        val params = requestContext.request.uri.query.toMap
                        val requestType = params.getOrElse("reqtype", "")
                        val resIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid param resource IRI: $iri"))

                        requestType match {
                            case "properties" => ResourceFullGetRequestV1(resIri, userProfile)
                            case other => throw BadRequestException(s"Invalid request type: $other")
                        }
                    }

                    RouteUtilV1.runHtmlRoute[ResourcesResponderRequestV1, ResourceFullResponseV1](
                        requestMessageTry,
                        ResourceHtmlView.propertiesHtmlView,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }
}
