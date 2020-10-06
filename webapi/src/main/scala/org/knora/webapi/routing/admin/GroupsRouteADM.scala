/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.sharedtestdata.SharedTestDataADM


object GroupsRouteADM {
    val GroupsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "groups")
}

/**
 * Provides a spray-routing function for API routes that deal with groups.
 */

@Api(value = "groups", produces = "application/json")
@Path("/admin/groups")
class GroupsRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with GroupsADMJsonProtocol {

    import GroupsRouteADM._
    
    /**
     * Returns all groups
     */
    private def getGroups: Route = path(GroupsBasePath) {
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
        }
    }

    /**
     * Creates a group
     */
    private def createGroup: Route = path(GroupsBasePath) {
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
    }

    /**
     * Returns a single group identified by IRI.
     */
    private def getGroupByIri: Route = path(GroupsBasePath / Segment) { value =>
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
        }
    }

    /**
     * Update basic group information.
     */
    private def updateGroup: Route = path(GroupsBasePath / Segment) { value =>
        put {
            /* update a group identified by iri */
            entity(as[ChangeGroupApiRequestADM]) { apiRequest =>
                requestContext =>
                    val checkedGroupIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid group IRI $value"))

                    /**
                     * The api request is already checked at time of creation.
                     * See case class.
                     */

                    if (apiRequest.status.nonEmpty) {
                        throw BadRequestException("The status property is not allowed to be set for this route. Please use the change status route.")
                    }

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
        }
    }

    /**
     * Update the group's status.
     */
    private def changeGroupStatus: Route = path(GroupsBasePath / Segment / "status") { value =>
        put {
            /* change the status of a group identified by iri */
            entity(as[ChangeGroupApiRequestADM]) { apiRequest =>
                requestContext =>
                    val checkedGroupIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid group IRI $value"))

                    /**
                     * The api request is already checked at time of creation.
                     * See case class. Depending on the data sent, we are either
                     * doing a general update or status change. Since we are in
                     * the status change route, we are only interested in the
                     * value of the status property
                     */

                    if (apiRequest.status.isEmpty) {
                        throw BadRequestException("The status property is not allowed to be empty.")
                    }

                    val requestMessage = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield GroupChangeStatusRequestADM(
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
        }
    }

    /**
     * Deletes a group (sets status to false)
     */
    private def deleteGroup: Route = path(GroupsBasePath / Segment) { value =>
        delete {
            /* update group status to false */
            requestContext =>
                val checkedGroupIri = stringFormatter.validateAndEscapeIri(value, throw BadRequestException(s"Invalid group IRI $value"))

                val requestMessage = for {
                    requestingUser <- getUserADM(requestContext)
                } yield GroupChangeStatusRequestADM(
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
    }

    /**
     * Gets members of single group.
     */
    private def getGroupMembers: Route = path(GroupsBasePath / Segment / "members") { value =>
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

    /**
     * Returns the route.
     */
    override def knoraApiPath: Route = getGroups ~ createGroup ~ getGroupByIri ~
        updateGroup ~ changeGroupStatus ~ deleteGroup ~ getGroupMembers

}
