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
import org.knora.webapi.messages.v1respondermessages.usermessages.UserProfileV1
import org.knora.webapi.messages.v1respondermessages.valuemessages.ApiValueV1JsonProtocol._
import org.knora.webapi.messages.v1respondermessages.valuemessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.{DateUtilV1, InputValidation}
import org.knora.webapi.{BadRequestException, IRI, SettingsImpl}
import spray.json.JsonParser
import spray.routing.Directives._
import spray.routing._

import scala.util.Try

/**
  * Provides a spray-routing function for API routes that deal with values.
  */
object ValuesRouteV1 extends Authenticator {

    private def makeVersionHistoryRequestMessage(userProfile: UserProfileV1, iris: Seq[IRI]): ValueVersionHistoryGetRequestV1 = {
        if (iris.length != 3) throw BadRequestException("Version history request requires resource IRI, property IRI, and current value IRI")

        val Vector(resourceIriStr, propertyIriStr, currentValueIriStr) = iris

        val resourceIri = InputValidation.toIri(resourceIriStr, () => throw BadRequestException(s"Invalid resource IRI $resourceIriStr"))
        val propertyIri = InputValidation.toIri(propertyIriStr, () => throw BadRequestException(s"Invalid property IRI $propertyIriStr"))
        val currentValueIri = InputValidation.toIri(currentValueIriStr, () => throw BadRequestException(s"Invalid value IRI $currentValueIriStr"))

        ValueVersionHistoryGetRequestV1(
            resourceIri = resourceIri,
            propertyIri = propertyIri,
            currentValueIri = currentValueIri,
            userProfile = userProfile
        )
    }

    private def makeLinkValueGetRequestMessage(userProfile: UserProfileV1, iris: Seq[IRI]): LinkValueGetRequestV1 = {
        if (iris.length != 3) throw BadRequestException("Link value request requires subject IRI, predicate IRI, and object IRI")

        val Vector(subjectIriStr, predicateIriStr, objectIriStr) = iris

        val subjectIri = InputValidation.toIri(subjectIriStr, () => throw BadRequestException(s"Invalid subject IRI $subjectIriStr"))
        val predicateIri = InputValidation.toIri(predicateIriStr, () => throw BadRequestException(s"Invalid predicate IRI $predicateIriStr"))
        val objectIri = InputValidation.toIri(objectIriStr, () => throw BadRequestException(s"Invalid object IRI $objectIriStr"))

        LinkValueGetRequestV1(
            subjectIri = subjectIri,
            predicateIri = predicateIri,
            objectIri = objectIri,
            userProfile = userProfile
        )
    }

    private def makeCreateValueRequestMessage(userProfile: UserProfileV1, apiRequest: CreateValueApiRequestV1): CreateValueRequestV1 = {
        val projectIri = InputValidation.toIri(apiRequest.project_id, () => throw BadRequestException(s"Invalid project IRI ${apiRequest.project_id}"))
        val resourceIri = InputValidation.toIri(apiRequest.res_id, () => throw BadRequestException(s"Invalid resource IRI ${apiRequest.res_id}"))
        val propertyIri = InputValidation.toIri(apiRequest.prop, () => throw BadRequestException(s"Invalid property IRI ${apiRequest.prop}"))

        // TODO: these match-case statements with lots of underlines are ugly and confusing.

        // TODO: Support the rest of the value types.
        val (value: UpdateValueV1, commentStr: Option[String]) = apiRequest match {
            case CreateValueApiRequestV1(_, _, _, _, Some(intValue: Int), _, _, _, _, comment) => (IntegerValueV1(intValue), comment)

            case CreateValueApiRequestV1(_, _, _, Some(richtext: CreateRichtextV1), _, _, _, _, _, comment) =>
                // textattr is a string that can be parsed into Map[String, Seq[StandoffPositionV1]]
                val textattr: Map[String, Seq[StandoffPositionV1]] = InputValidation.validateTextattr(JsonParser(richtext.textattr).convertTo[Map[String, Seq[StandoffPositionV1]]])
                val resourceReference: Seq[IRI] = InputValidation.validateResourceReference(richtext.resource_reference)

                (TextValueV1(InputValidation.toSparqlEncodedString(richtext.utf8str), textattr = textattr, resource_reference = resourceReference), comment)

            case CreateValueApiRequestV1(_, _, _, _, _, Some(floatValue: Float), _, _, _, comment) => (FloatValueV1(floatValue), comment)

            case CreateValueApiRequestV1(_, _, _, _, _, _, Some(dateStr: String), _, _, comment) =>
                (DateUtilV1.createJDCValueV1FromDateString(dateStr), comment)

            case CreateValueApiRequestV1(_, _, _, _, _, _, _, Some(colorStr: String), _, comment) =>
                val colorValue = InputValidation.toColor(colorStr, () => throw BadRequestException(s"Invalid color value $colorStr"))
                (ColorValueV1(colorValue), comment)

            case CreateValueApiRequestV1(_, _, _, _, _, _, _, _, Some(geomStr: String), comment) =>
                val geometryValue = InputValidation.toGeometryString(geomStr, () => throw BadRequestException(s"Invalid geometry value geomStr"))
                (GeomValueV1(geometryValue), comment)

            case _ => throw BadRequestException(s"No value submitted")
        }

        val maybeComment = commentStr.map(str => InputValidation.toSparqlEncodedString(str))

        CreateValueRequestV1(
            projectIri = projectIri,
            resourceIri = resourceIri,
            propertyIri = propertyIri,
            value = value,
            comment = maybeComment,
            userProfile = userProfile,
            apiRequestID = UUID.randomUUID
        )
    }

    private def makeAddValueVersionRequestMessage(userProfile: UserProfileV1, valueIriStr: IRI, apiRequest: ChangeValueApiRequestV1): ChangeValueRequestV1 = {
        val projectIri = InputValidation.toIri(apiRequest.project_id, () => throw BadRequestException(s"Invalid project IRI ${apiRequest.project_id}"))
        val valueIri = InputValidation.toIri(valueIriStr, () => throw BadRequestException(s"Invalid value IRI $valueIriStr"))

        // TODO: These match-case statements with lots of underlines are ugly and confusing.

        // TODO: Support the rest of the value types.
        val (value: UpdateValueV1, commentStr: Option[String]) = apiRequest match {
            case ChangeValueApiRequestV1(_, _, Some(intValue: Int), _, _, _, _, comment) => (IntegerValueV1(intValue), comment)

            case ChangeValueApiRequestV1(_, Some(richtext: CreateRichtextV1), _, _, _, _, _, comment) =>
                // textattr is a string that can be parsed into Map[String, Seq[StandoffPositionV1]]
                val textattr: Map[String, Seq[StandoffPositionV1]] = InputValidation.validateTextattr(JsonParser(richtext.textattr).convertTo[Map[String, Seq[StandoffPositionV1]]])
                val resourceReference: Seq[IRI] = InputValidation.validateResourceReference(richtext.resource_reference)

                (TextValueV1(InputValidation.toSparqlEncodedString(richtext.utf8str), textattr = textattr, resource_reference = resourceReference), comment)

            case ChangeValueApiRequestV1(_, _, _, Some(floatValue: Float), _, _, _, comment) => (FloatValueV1(floatValue), comment)

            case ChangeValueApiRequestV1(_, _, _, _, Some(dateStr: String), _, _, comment) =>
                (DateUtilV1.createJDCValueV1FromDateString(dateStr), comment)

            case ChangeValueApiRequestV1(_, _, _, _, _, Some(colorStr: String), _, comment) =>
                val colorValue = InputValidation.toColor(colorStr, () => throw BadRequestException(s"Invalid color value $colorStr"))
                (ColorValueV1(colorValue), comment)

            case ChangeValueApiRequestV1(_, _, _, _, _, _, Some(geomStr), comment) =>
                val geometryValue = InputValidation.toGeometryString(geomStr, () => throw BadRequestException(s"Invalid geometry value geomStr"))
                (GeomValueV1(geometryValue), comment)

            case ChangeValueApiRequestV1(_, _, _, _, _, _, _, Some(comment)) =>
                throw BadRequestException(s"No value was submitted")

            case _ => throw BadRequestException(s"No value or comment was submitted")
        }

        val maybeComment = commentStr.map(str => InputValidation.toSparqlEncodedString(str))

        ChangeValueRequestV1(
            valueIri = valueIri,
            value = value,
            comment = maybeComment,
            userProfile = userProfile,
            apiRequestID = UUID.randomUUID
        )
    }

    private def makeChangeCommentRequestMessage(userProfile: UserProfileV1, valueIriStr: IRI, commentStr: String): ChangeCommentRequestV1 = {
        val valueIri = InputValidation.toIri(valueIriStr, () => throw BadRequestException(s"Invalid value IRI $valueIriStr"))
        val comment = InputValidation.toSparqlEncodedString(commentStr)

        ChangeCommentRequestV1(
            valueIri = valueIri,
            comment = comment,
            userProfile = userProfile,
            apiRequestID = UUID.randomUUID
        )
    }

    private def makeDeleteValueRequest(userProfile: UserProfileV1, valueIriStr: IRI): DeleteValueRequestV1 = {
        val valueIri = InputValidation.toIri(valueIriStr, () => throw BadRequestException(s"Invalid value IRI $valueIriStr"))
        DeleteValueRequestV1(
            valueIri = valueIri,
            userProfile = userProfile,
            apiRequestID = UUID.randomUUID
        )
    }

    private def makeGetValueRequest(valueIriStr: IRI, userProfile: UserProfileV1): ValueGetRequestV1 = {
        val valueIri = InputValidation.toIri(valueIriStr, () => throw BadRequestException(s"Invalid value IRI $valueIriStr"))
        ValueGetRequestV1(valueIri, userProfile)
    }

    def rapierPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "values" / "history" / Segments) { iris =>
            get {
                requestContext => {
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        makeVersionHistoryRequestMessage(userProfile, iris)
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
        } ~ path("v1" / "values") {
            post {
                entity(as[CreateValueApiRequestV1]) { apiRequest => requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        makeCreateValueRequestMessage(userProfile, apiRequest)
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
        } ~ path("v1" / "values" / Segment) { valueIri =>
            get {
                requestContext => {
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        makeGetValueRequest(valueIri, userProfile)
                    }

                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            } ~ put {
                entity(as[ChangeValueApiRequestV1]) { apiRequest => requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)

                        // TODO: handle a fileValue request (without binary data contained in the http request)
                        apiRequest match {
                            case ChangeValueApiRequestV1(_, _, _, _, _, _, _, Some(comment)) => makeChangeCommentRequestMessage(userProfile, valueIri, comment)
                            case _ => makeAddValueVersionRequestMessage(userProfile, valueIri, apiRequest)
                        }
                    }

                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            } ~ delete {
                requestContext => {
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        makeDeleteValueRequest(userProfile, valueIri)
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
        } ~ path("v1" / "links" / Segments) { iris =>
            get {
                requestContext => {
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        makeLinkValueGetRequestMessage(userProfile, iris)
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
        }
    }
}
