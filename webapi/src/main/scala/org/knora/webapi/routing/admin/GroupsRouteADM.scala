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

package org.knora.webapi.routing.admin

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.apache.commons.validator.routines.UrlValidator
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilADM}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{BadRequestException, SettingsImpl}

object GroupsRouteADM extends Authenticator with GroupsADMJsonProtocol {

    private val schemes = Array("http", "https")
    private val urlValidator = new UrlValidator(schemes)

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {

        implicit val system: ActorSystem = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")
        val stringFormatter = StringFormatter.getGeneralInstance

        path("admin" / "groups") {
            get {
                /* return all groups */
                requestContext =>
                    val requestingUser = getUserADM(requestContext)

                    val requestMessage = GroupsGetRequestADM(requestingUser)

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            } ~
            post {
                /* create a new group */
                entity(as[CreateGroupApiRequestADM]) { apiRequest =>
                    requestContext =>
                        val requestingUser = getUserADM(requestContext)

                        val requestMessage = GroupCreateRequestADM(
                            createRequest = apiRequest,
                            requestingUser = requestingUser,
                            apiRequestID = UUID.randomUUID()
                        )

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            }
        } ~
        path("v1" / "groups" / Segment) { value =>
            get {
                /* returns a single group identified through iri */
                requestContext =>
                    val requestingUser = getUserADM(requestContext)

                    val checkedGroupIri = stringFormatter.validateAndEscapeIri(value, () => throw BadRequestException(s"Invalid group IRI $value"))
                    val requestMessage = GroupGetRequestADM(checkedGroupIri, requestingUser)

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            } ~
            put {
                /* update a group identified by iri */
                entity(as[ChangeGroupApiRequestADM]) { apiRequest =>
                    requestContext =>
                        val requestingUser = getUserADM(requestContext)
                        val checkedGroupIri = stringFormatter.validateAndEscapeIri(value, () => throw BadRequestException(s"Invalid group IRI $value"))

                        /* the api request is already checked at time of creation. see case class. */

                        val requestMessage = GroupChangeRequestADM(
                            groupIri = checkedGroupIri,
                            changeGroupRequest = apiRequest,
                            requestingUser = requestingUser,
                            apiRequestID = UUID.randomUUID()
                        )

                        RouteUtilADM.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            } ~
            delete {
                /* update group status to false */
                requestContext =>
                    val requestingUser = getUserADM(requestContext)
                    val checkedGroupIri = stringFormatter.validateAndEscapeIri(value, () => throw BadRequestException(s"Invalid group IRI $value"))

                    val requestMessage = GroupChangeRequestADM(
                        groupIri = checkedGroupIri,
                        changeGroupRequest = ChangeGroupApiRequestADM(status = Some(false)),
                        requestingUser = requestingUser,
                        apiRequestID = UUID.randomUUID()
                    )

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~
        path("v1" / "groups" / "members" / Segment) { value =>
            get {
                /* returns all members of the group identified through iri or name/project */
                requestContext =>

                    val requestingUser = getUserADM(requestContext)
                    val checkedGroupIri = stringFormatter.validateAndEscapeIri(value, () => throw BadRequestException(s"Invalid group IRI $value"))

                    val requestMessage = GroupMembersGetRequestADM(groupIri = checkedGroupIri, requestingUser = requestingUser)

                    RouteUtilADM.runJsonRoute(
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
