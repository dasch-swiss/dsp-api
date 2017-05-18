/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.apache.commons.validator.routines.UrlValidator
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.InputValidation
import org.knora.webapi.{BadRequestException, SettingsImpl}

object ProjectsRouteV1 extends Authenticator with ProjectV1JsonProtocol {

    private val schemes = Array("http", "https")
    private val urlValidator = new UrlValidator(schemes)

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {

        implicit val system: ActorSystem = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "projects") {
            get {
                /* returns all projects */
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val requestMessage = ProjectsGetRequestV1(Some(userProfile))
                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            } ~
            post {
                /* create a new project */
                entity(as[CreateProjectApiRequestV1]) { apiRequest => requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val requestMessage = ProjectCreateRequestV1(
                        createRequest = apiRequest,
                        userProfileV1 = userProfile,
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
        } ~ path("v1" / "projects"/ Segment) { value =>
            get {
                /* returns a single project identified either through iri or shortname */
                parameters("identifier" ? "iri") { identifier: String => requestContext =>

                    val userProfile = getUserProfileV1(requestContext)

                    val requestMessage = if (identifier != "iri") { // identify project by shortname.
                        val shortNameDec = java.net.URLDecoder.decode(value, "utf-8")
                        ProjectInfoByShortnameGetRequestV1(shortNameDec, Some(userProfile))
                    } else { // identify project by iri. this is the default case.
                        val checkedProjectIri = InputValidation.toIri(value, () => throw BadRequestException(s"Invalid project IRI $value"))
                        ProjectInfoByIRIGetRequestV1(checkedProjectIri, Some(userProfile))
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
                /* update a project identified by iri */
                entity(as[ChangeProjectApiRequestV1]) { apiRequest => requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val checkedProjectIri = InputValidation.toIri(value, () => throw BadRequestException(s"Invalid project IRI $value"))

                    val requestMessage = ProjectChangeRequestV1(
                        projectIri = checkedProjectIri,
                        changeProjectRequest = apiRequest,
                        userProfileV1 = userProfile,
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
        } ~ path("v1" / "projects" / "members" / Segment) { value =>
            get {
                /* returns all members part of a project identified through iri or shortname */
                parameters("identifier" ? "iri") { identifier: String =>
                    requestContext =>

                        val userProfile = getUserProfileV1(requestContext)

                        val requestMessage = if (identifier != "iri") {// identify project by shortname.
                            val shortNameDec = java.net.URLDecoder.decode(value, "utf-8")
                            ProjectMembersByShortnameGetRequestV1(shortNameDec, userProfile)
                        } else {
                            val checkedProjectIri = InputValidation.toIri(value, () => throw BadRequestException(s"Invalid project IRI $value"))
                            ProjectMembersByIRIGetRequestV1(checkedProjectIri, userProfile)
                        }

                        RouteUtilV1.runJsonRoute(
                            requestMessage,
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
