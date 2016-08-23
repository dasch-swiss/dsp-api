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
import org.apache.commons.validator.routines.UrlValidator
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoByIRIGetRequest, ProjectInfoType, ProjectsGetRequestV1, ProjectInfoByShortnameGetRequest}
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.InputValidation
import org.knora.webapi.{BadRequestException, SettingsImpl}
import spray.routing.Directives._
import spray.routing._

import scala.util.Try

object ProjectsRouteV1 extends Authenticator {

    private val schemes = Array("http", "https")
    private val urlValidator = new UrlValidator(schemes)

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
                        val params = requestContext.request.uri.query.toMap
                        val infoType = params.getOrElse("infoType", ProjectInfoType.SHORT.toString)
                        ProjectsGetRequestV1(ProjectInfoType.lookup(infoType), Some(userProfile))
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
            path("v1" / "projects" / Segment) { value =>
                get {
                    requestContext =>
                        val requestMessageTry = Try {
                            val userProfile = getUserProfileV1(requestContext)
                            val params = requestContext.request.uri.query.toMap
                            val requestType = params.getOrElse("infoType", ProjectInfoType.SHORT.toString)
                            if (urlValidator.isValid(value)) {
                                /* valid URL */
                                ProjectInfoByIRIGetRequest(value, ProjectInfoType.lookup(requestType), Some(userProfile))
                            } else {
                                /* not valid URL so I assume it is an username */
                                ProjectInfoByShortnameGetRequest(value, ProjectInfoType.lookup(requestType), Some(userProfile))
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
            }
    }
}
