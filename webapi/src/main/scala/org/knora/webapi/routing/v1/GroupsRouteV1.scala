/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.apache.commons.validator.routines.UrlValidator
import org.knora.webapi.messages.v1.responder.groupmessages.{GroupInfoByIRIGetRequest, GroupInfoByNameGetRequest, GroupInfoType, GroupsGetRequestV1}
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.InputValidation
import org.knora.webapi.{BadRequestException, SettingsImpl}

import scala.util.Try

object GroupsRouteV1 extends Authenticator {

    private val schemes = Array("http", "https")
    private val urlValidator = new UrlValidator(schemes)

    def rapierPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {

        implicit val system: ActorSystem = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "groups") {
            get {
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val params = requestContext.request.uri.query().toMap
                    val infoType = params.getOrElse("infoType", GroupInfoType.SHORT.toString)
                    val requestMessage = GroupsGetRequestV1(GroupInfoType.lookup(infoType), Some(userProfile))
                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~ path("v1" / "groups" / Segment) { value =>
            get {
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val params = requestContext.request.uri.query().toMap
                    val infoType = params.getOrElse("infoType", GroupInfoType.SHORT.toString)
                    val projectIriValue = params.getOrElse("projectIri", "")
                    val projectIri = InputValidation.toIri(projectIriValue, () => throw BadRequestException(s"Invalid project IRI supplied: $projectIriValue"))
                    val requestMessage = if (urlValidator.isValid(value)) {
                        /* valid URL */
                        GroupInfoByIRIGetRequest(value, GroupInfoType.lookup(infoType), Some(userProfile))
                    } else {
                        /* not valid URL so I assume it is the group's name */
                        GroupInfoByNameGetRequest(projectIri, value, GroupInfoType.lookup(infoType), Some(userProfile))
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
