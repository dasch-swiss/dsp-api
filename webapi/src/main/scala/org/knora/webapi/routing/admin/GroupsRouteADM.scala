/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.routing.admin

import java.util.UUID

import akka.actor.{ActorSelection, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_PATH
import org.knora.webapi.routing.{Authenticator, RouteUtilADM}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{BadRequestException, KnoraDispatchers, SettingsImpl}

import scala.concurrent.ExecutionContextExecutor

/**
  * Provides a spray-routing function for API routes that deal with groups.
  */

@Api(value = "groups", produces = "application/json")
@Path("/admin/groups")
class GroupsRouteADM(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter) extends Authenticator with GroupsADMJsonProtocol {

    implicit val system: ActorSystem = _system
    implicit val executionContext: ExecutionContextExecutor = system.dispatchers.lookup(KnoraDispatchers.KnoraAskDispatcher)
    implicit val timeout: Timeout = settings.defaultTimeout
    val responderManager: ActorSelection = system.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)
    val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    def knoraApiPath: Route = {

        path("admin" / "groups") {
            get {
                /* return all groups */
                requestContext =>
                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield GroupsGetRequestADM(requestingUser)

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
                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield GroupCreateRequestADM(
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
        path("admin" / "groups" / Segment) { value =>
            get {
                /* returns a single group identified through iri */
                requestContext =>
                    val checkedGroupIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid group IRI $value"))

                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield GroupGetRequestADM(checkedGroupIri, requestingUser)

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
                        val checkedGroupIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid group IRI $value"))

                        /* the api request is already checked at time of creation. see case class. */

                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield GroupChangeRequestADM(
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
                    val checkedGroupIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid group IRI $value"))

                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield GroupChangeRequestADM(
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
        path("admin" / "groups" / "members" / Segment) { value =>
            get {
                /* returns all members of the group identified through iri */
                requestContext =>
                    val checkedGroupIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid group IRI $value"))

                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield GroupMembersGetRequestADM(groupIri = checkedGroupIri, requestingUser = requestingUser)

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
