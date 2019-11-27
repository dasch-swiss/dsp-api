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
import akka.stream.ActorMaterializer
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.clientapi.EndpointFunctionDSL._
import org.knora.webapi.util.clientapi._
import org.knora.webapi.{BadRequestException, OntologyConstants, SharedTestDataADM}

import scala.concurrent.{ExecutionContext, Future}


object GroupsRouteADM {
    val GroupsBasePath = PathMatcher("admin" / "groups")
    val GroupsBasePathString: String = "/admin/groups"
}

/**
 * Provides a spray-routing function for API routes that deal with groups.
 */

@Api(value = "groups", produces = "application/json")
@Path("/admin/groups")
class GroupsRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with GroupsADMJsonProtocol with ClientEndpoint {

    import GroupsRouteADM._

    /**
     * The name of this [[ClientEndpoint]].
     */
    override val name: String = "GroupsEndpoint"

    /**
     * The directory name to be used for this endpoint's code.
     */
    override val directoryName: String = "groups"

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
    private val CreateGroupRequest = classRef(OntologyConstants.KnoraAdminV2.CreateGroupRequest.toSmartIri)
    private val UpdateGroupRequest = classRef(OntologyConstants.KnoraAdminV2.UpdateGroupRequest.toSmartIri)

    private val groupIri = SharedTestDataADM.imagesReviewerGroup.id
    private val groupIriEnc = java.net.URLEncoder.encode(groupIri, "utf-8")

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

    private val getGroupsFunction: ClientFunction =
        "getGroups" description "Returns a list of all groups." params() doThis {
            httpGet(BasePath)
        } returns GroupsResponse

    private def getGroupsTestResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(baseApiUrl + GroupsBasePathString) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.imagesUser01.email, SharedTestDataADM.testPass)))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-groups-response"),
            text = responseStr
        )
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

    private val createGroupFunction: ClientFunction =
        "createGroup" description "Creates a group." params (
            "group" description "The group to be created." paramType CreateGroupRequest
            ) doThis {
            httpPost(
                path = BasePath,
                body = Some(arg("group"))
            )
        } returns GroupResponse

    private def createGroupTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("create-group-request"),
                text = SharedTestDataADM.createGroupRequest
            )
        )
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

    private val getGroupByIriFunction: ClientFunction =
        "getGroupByIri" description "Gets a group by IRI." params (
            "iri" description "The IRI of the group." paramType UriDatatype
            ) doThis {
            httpGet(arg("iri"))
        } returns GroupResponse

    private def getGroupByIriTestResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$GroupsBasePathString/$groupIriEnc") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.imagesUser01.email, SharedTestDataADM.testPass)))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-group-response"),
            text = responseStr
        )
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

    private val updateGroupFunction: ClientFunction =
        "updateGroup" description "Updates a group." params (
            "iri" description "The IRI of the group to be updated." paramType UriDatatype,
            "groupInfo" description "The group information to be updated." paramType UpdateGroupRequest
            ) doThis {
            httpPut(
                path = arg("iri"),
                body = Some(arg("groupInfo"))
            )
        } returns GroupResponse

    private def updateGroupTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("update-group-request"),
                text = SharedTestDataADM.updateGroupRequest
            )
        )
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

    private val changeGroupStatusFunction: ClientFunction =
        "updateGroupStatus" description "Updates the status of a group." params (
            "iri" description "The IRI of the group to be updated." paramType UriDatatype,
            "status" description "The new status of the group." paramType BooleanDatatype
            ) doThis {
            httpPut(
                path = arg("iri") / str("status"),
                body = Some(json(
                    "status" -> arg("status")
                ))
            )
        } returns GroupResponse

    private def changeGroupStatusTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("change-group-status-request"),
                text = SharedTestDataADM.changeGroupStatusRequest
            )
        )
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

    private val deleteGroupFunction: ClientFunction =
        "deleteGroup" description "Deletes a group. This method does not actually delete a group, but sets the status to false." params (
            "iri" description "The IRI of the group." paramType UriDatatype
            ) doThis {
            httpDelete(
                path = arg("iri")
            )
        } returns GroupResponse


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

    private val getGroupMembersFunction: ClientFunction =
        "getGroupMembers" description "Gets the members of a group." params (
            "iri" description "The IRI of the group." paramType UriDatatype
            ) doThis {
            httpGet(arg("iri") / str("members"))
        } returns MembersResponse

    private def getGroupMembersTestResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$GroupsBasePathString/$groupIriEnc/members") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.imagesUser01.email, SharedTestDataADM.testPass)))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-group-members-response"),
            text = responseStr
        )
    }

    /**
     * All defined routes need to be combined here.
     */
    override def knoraApiPath: Route = getGroups ~ createGroup ~ getGroupByIri ~
        updateGroup ~ changeGroupStatus ~ deleteGroup ~ getGroupMembers

    /**
     * The functions defined by this [[ClientEndpoint]].
     */
    override val functions: Seq[ClientFunction] = Seq(
        getGroupsFunction,
        createGroupFunction,
        getGroupByIriFunction,
        updateGroupFunction,
        changeGroupStatusFunction,
        deleteGroupFunction,
        getGroupMembersFunction
    )

    /**
     * Returns test data for this endpoint.
     *
     * @return a set of test data files to be used for testing this endpoint.
     */
    override def getTestData(implicit executionContext: ExecutionContext,
                             actorSystem: ActorSystem,
                             materializer: ActorMaterializer): Future[Set[SourceCodeFileContent]] = {
        Future.sequence {
            Set(
                getGroupsTestResponse,
                createGroupTestRequest,
                getGroupByIriTestResponse,
                updateGroupTestRequest,
                changeGroupStatusTestRequest,
                getGroupMembersTestResponse
            )
        }
    }
}
