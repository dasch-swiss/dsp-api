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

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoByIRIGetRequest, ProjectInfoType, ProjectsGetRequestV1}
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.InputValidation
import org.knora.webapi.{BadRequestException, SettingsImpl}

import scala.util.Try

object ProjectsRouteV1 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {

        implicit val system: ActorSystem = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "projects") {
            get {
                requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        ProjectsGetRequestV1(Some(userProfile))
                    }
                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~
            path("v1" / "projects" / Segment) { iri =>
                get {
                    // TODO: here, we should differentiate between a given project Iri and a project shortname
                    parameters("reqtype".?) { reqtypeParam =>
                        requestContext =>
                            val requestMessageTry = Try {
                                val userProfile = getUserProfileV1(requestContext)
                                val requestType = reqtypeParam.getOrElse(ProjectInfoType.SHORT.toString)
                                val resIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid param resource IRI: $iri"))
                                ProjectInfoByIRIGetRequest(resIri, ProjectInfoType.lookup(requestType), Some(userProfile))
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
