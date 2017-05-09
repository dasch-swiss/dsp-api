/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing.v1

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.apache.commons.validator.routines.UrlValidator
import org.knora.webapi.messages.v1.responder.groupmessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.InputValidation
import org.knora.webapi.{BadRequestException, IRI, SettingsImpl}

object GroupsRouteV1 extends Authenticator {

    import GroupV1JsonProtocol._

    private val schemes = Array("http", "https")
    private val urlValidator = new UrlValidator(schemes)

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {

        implicit val system: ActorSystem = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "groups") {
            get {
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val params = requestContext.request.uri.query().toMap
                    val requestMessage = GroupsGetRequestV1(Some(userProfile))
                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            } ~ post {
                entity(as[CreateGroupApiRequestV1]) { apiRequest => requestContext =>
                    val userProfile = getUserProfileV1(requestContext)

                    val requestMessage = GroupCreateRequestV1(
                        createRequest = apiRequest,
                        userProfile,
                        apiRequestID = UUID.randomUUID()
                    )

                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            }
        } ~ path("v1" / "groups" / Segment) { value =>
            get {
                parameters("identifier" ? "iri", 'projectIri.as[IRI].?) { (identifier: String, maybeProjectIri: Option[IRI]) =>
                    requestContext =>

                        val userProfile = getUserProfileV1(requestContext)

                        val requestMessage = if (identifier == "groupname"){ // identify group by groupname/projectIri
                            maybeProjectIri match {
                                case Some(projectIri) => {

                                    val ckeckedProjectIri = InputValidation.toIri(projectIri, () => throw BadRequestException(s"Invalid project IRI $projectIri"))
                                    println(s"groupname case - value: $value, projectIri: ${ckeckedProjectIri}")
                                    GroupInfoByNameGetRequest(ckeckedProjectIri, value, Some(userProfile))
                                }
                                case None => throw BadRequestException("Missing project IRI")
                            }

                        } else { // identify group by iri. this is the default case
                            println(s"iri case: $value")
                            val checkedGroupIri = InputValidation.toIri(value, () => throw BadRequestException(s"Invalid group IRI $value"))
                            GroupInfoByIRIGetRequest(checkedGroupIri, Some(userProfile))
                        }

                        RouteUtilV1.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            } ~
            put {
                /* change group */
                entity(as[ChangeGroupApiRequestV1]) { apiRequest =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)
                        val checkedGroupIri = InputValidation.toIri(value, () => throw BadRequestException(s"Invalid group IRI $value"))
                        val requestMessage = GroupChangeRequestV1(
                            groupIri = checkedGroupIri,
                            changeGroupRequest = apiRequest,
                            userProfile = userProfile,
                            apiRequestID = UUID.randomUUID()
                        )
                        RouteUtilV1.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            }
        } ~
        path("v1" / "groups" / "members" / Segment) { value =>
            get {
                /* get members for the supplied group IRI */
                complete("not implemented")
            }
        }
            /*
            ~
        path ("v1" / "groups" / "members" / Segment / Segment) { groupIri => userIri =>
            post {
                /* add user to  group */
                complete("not implemented")
            } ~
            delete {
                /* remove user from project member group */
                complete("not implemented")
            }
        }
        */
    }
}
