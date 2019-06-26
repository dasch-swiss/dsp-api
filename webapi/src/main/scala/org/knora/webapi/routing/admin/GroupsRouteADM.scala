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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.clientapi.EndpointFunctionDSL._
import org.knora.webapi.util.clientapi._
import org.knora.webapi.{BadRequestException, OntologyConstants}


object GroupsRouteADM {
    val GroupsBasePath = PathMatcher("admin" / "groups")
}

/**
  * Provides a spray-routing function for API routes that deal with groups.
  */

@Api(value = "groups", produces = "application/json")
@Path("/admin/groups")
class GroupsRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with GroupsADMJsonProtocol with ClientEndpoint {

    import GroupsRouteADM.GroupsBasePath

    /**
      * The name of this [[ClientEndpoint]].
      */
    override val name: String = "GroupsEndpoint"

    /**
      * The URL path of this [[ClientEndpoint]].
      */
    override val urlPath: String = "/groups"

    /**
      * A description of this [[ClientEndpoint]].
      */
    override val description: String = "An endpoint for working with Knora groups."

    // Classes used in client function definitions.

    private val GroupsResponse = classRef(OntologyConstants.KnoraAdminV2.GroupsResponse.toSmartIri)
    private val GroupResponse = classRef(OntologyConstants.KnoraAdminV2.GroupResponse.toSmartIri)
    private val MembersResponse = classRef(OntologyConstants.KnoraAdminV2.MembersResponse.toSmartIri)
    private val Group = classRef(OntologyConstants.KnoraAdminV2.GroupClass.toSmartIri)

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

    private val getGroupsFunction: ClientFunction =
        "getGroups" description "Returns a list of all groups." params() doThis {
            httpGet(BasePath)
        } returns GroupsResponse

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

    private val createGroupFunction: ClientFunction =
        "createGroup" description "Creates a group." params (
            "group" description "The group to be created." paramType Group
            ) doThis {
            httpPost(
                path = BasePath,
                body = Some(arg("group"))
            )
        } returns GroupResponse

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

    private val getGroupByIriFunction: ClientFunction =
        "getGroupByIri" description "Gets a group by IRI." params(
            "iri" description "The IRI of the group." paramType UriDatatype
        ) doThis {
            httpGet(arg("iri"))
        } returns GroupResponse

    private def updateGroup: Route = path(GroupsBasePath / Segment) { value =>
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
        }
    }

    private val updateGroupFunction: ClientFunction =
        "updateGroup" description "Updates a group." params(
            "group" description "The group to be updated." paramType Group
        ) doThis {
            httpPut(
                path = argMember("group", "id"),
                body = Some(arg("group"))
            )
        } returns GroupResponse

    private def deleteGroup: Route = path(GroupsBasePath / Segment) { value =>
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
    }

    private val deleteGroupFunction: ClientFunction =
        "deleteGroup" description "Deletes a group. This method does not actually delete a group, but sets the status to false." params (
            "group" description "The group to be deleted." paramType Group
            ) doThis {
            httpDelete(
                path = argMember("group", "id")
            )
        } returns GroupResponse

    private def getGroupMembers: Route = path(GroupsBasePath / "members" / Segment) { value =>
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

    private val getGroupMembersFunction: ClientFunction =
        "getGroupMembers" description "Gets the members of a group." params(
            "group" description "The group." paramType Group
            ) doThis {
            httpGet(str("members") / argMember("group", "id"))
        } returns MembersResponse

    override def knoraApiPath: Route = getGroups ~ createGroup ~ getGroupByIri ~
        updateGroup ~ deleteGroup ~ getGroupMembers

    /**
      * The functions defined by this [[ClientEndpoint]].
      */
    override val functions: Seq[ClientFunction] = Seq(
        getGroupsFunction,
        createGroupFunction,
        getGroupByIriFunction,
        updateGroupFunction,
        deleteGroupFunction,
        getGroupMembersFunction
    )
}
