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

import java.net.URLEncoder
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
import org.knora.webapi.annotation.ApiMayChange
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.clientapi.EndpointFunctionDSL._
import org.knora.webapi.util.clientapi._
import org.knora.webapi.{BadRequestException, KnoraSystemInstances, OntologyConstants, SharedTestDataADM}

import scala.concurrent.{ExecutionContext, Future}


object UsersRouteADM {
    val UsersBasePath = PathMatcher("admin" / "users")
    val UsersBasePathString: String = "/admin/users"
}

/**
 * Provides an akka-http-routing function for API routes that deal with users.
 */
@Api(value = "users", produces = "application/json")
@Path("/admin/users")
class UsersRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with ClientEndpoint {

    import UsersRouteADM._

    /**
     * The name of this [[ClientEndpoint]].
     */
    override val name: String = "UsersEndpoint"

    /**
     * The directory name to be used for this endpoint's code.
     */
    override val directoryName: String = "users"

    /**
     * The URL path of this [[ClientEndpoint]].
     */
    override val urlPath: String = "/users"

    /**
     * A description of this [[ClientEndpoint]].
     */
    override val description: String = "An endpoint for working with Knora users."

    // Classes used in client function definitions.

    private val UsersResponse = classRef(OntologyConstants.KnoraAdminV2.UsersResponse.toSmartIri)
    private val UserResponse = classRef(OntologyConstants.KnoraAdminV2.UserResponse.toSmartIri)
    private val ProjectsResponse = classRef(OntologyConstants.KnoraAdminV2.ProjectsResponse.toSmartIri)
    private val GroupsResponse = classRef(OntologyConstants.KnoraAdminV2.GroupsResponse.toSmartIri)
    private val User = classRef(OntologyConstants.KnoraAdminV2.UserClass.toSmartIri)
    private val UpdateUserRequest = classRef(OntologyConstants.KnoraAdminV2.UpdateUserRequest.toSmartIri)
    private val StoredUser = User.toStoredClassRef

    private val anythingUser1IriEnc = URLEncoder.encode(SharedTestDataADM.anythingUser1.id, "UTF-8")
    private val multiUserIriEnc = URLEncoder.encode(SharedTestDataADM.multiuserUser.id, "UTF-8")

    /* concatenate paths in the CORRECT order and return */
    override def knoraApiPath: Route =
        getUsers ~
            addUser ~ getUserByIri ~ getUserByEmail ~ getUserByUsername ~
            changeUserBasicInformation ~ changeUserPassword ~ changeUserStatus ~ deleteUser ~
            changeUserSytemAdminMembership ~
            getUsersProjectMemberships ~ addUserToProjectMembership ~ removeUserFromProjectMembership ~
            getUsersProjectAdminMemberships ~ addUserToProjectAdminMembership ~ removeUserFromProjectAdminMembership ~
            getUsersGroupMemberships ~ addUserToGroupMembership ~ removeUserFromGroupMembership


    @ApiOperation(value = "Get users", nickname = "getUsers", httpMethod = "GET", response = classOf[UsersGetResponseADM])
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /* return all users */
    def getUsers: Route = path(UsersBasePath) {
        get {
            requestContext =>
                val requestMessage: Future[UsersGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UsersGetRequestADM(requestingUser = requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getUsersFunction: ClientFunction =
        "getUsers" description "Returns a list of all users." params() doThis {
            httpGet(BasePath)
        } returns UsersResponse


    private def getUsersTestResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(baseApiUrl + UsersBasePathString) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-users-response"),
            text = responseStr
        )
    }

    @ApiOperation(value = "Add new user", nickname = "addUser", httpMethod = "POST", response = classOf[UserOperationResponseADM])
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"user\" to create", required = true,
            dataTypeClass = classOf[CreateUserApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /* create a new user */
    def addUser: Route = path(UsersBasePath) {
        post {
            entity(as[CreateUserApiRequestADM]) { apiRequest =>
                requestContext =>
                    val requestMessage: Future[UserCreateRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield UserCreateRequestADM(
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

    // #createUserFunction
    private val createUserFunction: ClientFunction =
        "createUser" description "Creates a user." params (
            "user" description "The user to be created." paramType User
            ) doThis {
            httpPost(
                path = BasePath,
                body = Some(arg("user"))
            )
        } returns UserResponse
    // #createUserFunction

    private def createUserTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("create-user-request"),
                text = SharedTestDataADM.createUserRequest
            )
        )
    }

    /**
     * return a single user identified by iri
     */
    private def getUserByIri: Route = path(UsersBasePath / "iri" / Segment) { value =>
        get {
            requestContext =>
                val requestMessage: Future[UserGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UserGetRequestADM(UserIdentifierADM(maybeIri = Some(value)), UserInformationTypeADM.RESTRICTED, requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    // #getUserFunction
    private val getUserFunction: ClientFunction =
        "getUser" description "Gets a user by a property." params(
            "property" description "The name of the property by which the user is identified." paramType enum("iri", "email", "username"),
            "value" description "The value of the property by which the user is identified." paramType StringDatatype
        ) doThis {
            httpGet(arg("property") / arg("value"))
        } returns UserResponse
    // #getUserFunction

    private def getUserTestResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$UsersBasePathString/iri/$anythingUser1IriEnc") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-user-response"),
            text = responseStr
        )
    }

    // #getUserByIriFunction
    private val getUserByIriFunction: ClientFunction =
        "getUserByIri" description "Gets a user by IRI." params (
            "iri" description "The IRI of the user." paramType UriDatatype
            ) doThis {
            getUserFunction withArgs(str("iri"), arg("iri") as StringDatatype)
        } returns UserResponse
    // #getUserByIriFunction

    /**
     * return a single user identified by email
     */
    private def getUserByEmail: Route = path(UsersBasePath / "email" / Segment) { value =>
        get {
            requestContext =>
                val requestMessage: Future[UserGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UserGetRequestADM(UserIdentifierADM(maybeEmail = Some(value)), UserInformationTypeADM.RESTRICTED, requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getUserByEmailFunction: ClientFunction =
        "getUserByEmail" description "Gets a user by email address." params (
            "email" description "The email address of the user." paramType StringDatatype
            ) doThis {
            getUserFunction withArgs(str("email"), arg("email"))
        } returns UserResponse

    /**
     * return a single user identified by username
     */
    private def getUserByUsername: Route = path(UsersBasePath / "username" / Segment) { value =>
        get {
            requestContext =>
                val requestMessage: Future[UserGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UserGetRequestADM(UserIdentifierADM(maybeUsername = Some(value)), UserInformationTypeADM.RESTRICTED, requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getUserByUsernameFunction: ClientFunction =
        "getUserByUsername" description "Gets a user by username." params (
            "username" description "The username of the user." paramType StringDatatype
            ) doThis {
            getUserFunction withArgs(str("username"), arg("username"))
        } returns UserResponse

    /**
     * API MAY CHANGE: Change existing user's basic information.
     */
    @ApiMayChange
    private def changeUserBasicInformation: Route = path(UsersBasePath / "iri" / Segment / "BasicUserInformation") { value =>
        put {
            entity(as[ChangeUserApiRequestADM]) { apiRequest =>
                requestContext =>

                    val userIri = stringFormatter.validateAndEscapeUserIri(value, throw BadRequestException(s"Invalid user IRI $value"))

                    if (userIri.equals(KnoraSystemInstances.Users.SystemUser.id) || userIri.equals(KnoraSystemInstances.Users.AnonymousUser.id)) {
                        throw BadRequestException("Changes to built-in users are not allowed.")
                    }

                    /* the api request is already checked at time of creation. see case class. */

                    val requestMessage: Future[UsersResponderRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield UserChangeBasicUserInformationRequestADM(
                        userIri,
                        changeUserRequest = apiRequest,
                        requestingUser,
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

    private val updateUserBasicInformationFunction: ClientFunction =
        "updateUserBasicInformation" description "Updates an existing user's basic information." params (
            "iri" description "The IRI of the user to be updated." paramType UriDatatype,
            "userInfo" description "The user information to be updated." paramType UpdateUserRequest
            ) doThis {
            httpPut(
                path = str("iri") / arg("iri") / str("BasicUserInformation"),
                body = Some(arg("userInfo"))
            )
        } returns UserResponse

    private def updateUserTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("update-user-request"),
                text = SharedTestDataADM.updateUserRequest
            )
        )
    }

    /**
     * API MAY CHANGE: Change user's password.
     */
    @ApiMayChange
    private def changeUserPassword: Route = path(UsersBasePath / "iri" / Segment / "Password") { value =>
        put {
            entity(as[ChangeUserApiRequestADM]) { apiRequest =>
                requestContext =>

                    val userIri = stringFormatter.validateAndEscapeUserIri(value, throw BadRequestException(s"Invalid user IRI $value"))

                    if (userIri.equals(KnoraSystemInstances.Users.SystemUser.id) || userIri.equals(KnoraSystemInstances.Users.AnonymousUser.id)) {
                        throw BadRequestException("Changes to built-in users are not allowed.")
                    }

                    /* the api request is already checked at time of creation. see case class. */

                    val requestMessage: Future[UsersResponderRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield UserChangePasswordRequestADM(
                        userIri = userIri,
                        changeUserRequest = apiRequest,
                        requestingUser,
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

    // #updateUserPasswordFunction
    private val updateUserPasswordFunction: ClientFunction =
        "updateUserPassword" description "Updates a user's password." params(
            "iri" description "The IRI of the user to be updated." paramType UriDatatype,
            "requesterPassword" description "The requesting user's current password." paramType StringDatatype,
            "newPassword" description "The specified user's new password." paramType StringDatatype
        ) doThis {
            httpPut(
                path = str("iri") / arg("iri") / str("Password"),
                body = Some(json(
                    "requesterPassword" -> arg("requesterPassword"),
                    "newPassword" -> arg("newPassword")
                ))
            )
        } returns UserResponse
    // #updateUserPasswordFunction

    private def updateUserPasswordTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("update-user-password-request"),
                text = SharedTestDataADM.changeUserPasswordRequest
            )
        )
    }

    /**
     * API MAY CHANGE: Change user's status.
     */
    @ApiMayChange
    private def changeUserStatus: Route = path(UsersBasePath / "iri" / Segment / "Status") { value =>
        put {
            entity(as[ChangeUserApiRequestADM]) { apiRequest =>
                requestContext =>

                    val userIri = stringFormatter.validateAndEscapeUserIri(value, throw BadRequestException(s"Invalid user IRI $value"))

                    if (userIri.equals(KnoraSystemInstances.Users.SystemUser.id) || userIri.equals(KnoraSystemInstances.Users.AnonymousUser.id)) {
                        throw BadRequestException("Changes to built-in users are not allowed.")
                    }

                    /* the api request is already checked at time of creation. see case class. */

                    val requestMessage: Future[UsersResponderRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield UserChangeStatusRequestADM(
                        userIri,
                        changeUserRequest = apiRequest,
                        requestingUser,
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

    private val updateUserStatusFunction: ClientFunction =
        "updateUserStatus" description "Updates a user's status." params (
            "iri" description "The user's IRI." paramType UriDatatype,
            "status" description "The user's new status." paramType BooleanDatatype
            ) doThis {
            httpPut(
                path = str("iri") / arg("iri") / str("Status"),
                body = Some(json("status" -> arg("status")))
            )
        } returns UserResponse

    private def updateUserStatusTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("update-user-status-request"),
                text = SharedTestDataADM.changeUserStatusRequest
            )
        )
    }

    /**
     * API MAY CHANGE: delete a user identified by iri (change status to false).
     */
    @ApiMayChange
    private def deleteUser: Route = path(UsersBasePath / "iri" / Segment) { value =>
        delete {
            requestContext => {
                val userIri = stringFormatter.validateAndEscapeUserIri(value, throw BadRequestException(s"Invalid user IRI $value"))

                if (userIri.equals(KnoraSystemInstances.Users.SystemUser.id) || userIri.equals(KnoraSystemInstances.Users.AnonymousUser.id)) {
                    throw BadRequestException("Changes to built-in users are not allowed.")
                }

                /* update existing user's status to false */
                val requestMessage: Future[UserChangeStatusRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UserChangeStatusRequestADM(
                    userIri,
                    changeUserRequest = ChangeUserApiRequestADM(status = Some(false)),
                    requestingUser,
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

    private val deleteUserFunction: ClientFunction =
        "deleteUser" description "Deletes a user. This method does not actually delete a user, but sets the status to false." params (
            "iri" description "The IRI of the user to be deleted." paramType UriDatatype
            ) doThis {
            httpDelete(
                path = str("iri") / arg("iri")
            )
        } returns UserResponse

    /**
     * API MAY CHANGE: Change user's SystemAdmin membership.
     */
    @ApiMayChange
    private def changeUserSytemAdminMembership: Route = path(UsersBasePath / "iri" / Segment / "SystemAdmin") { value =>
        put {
            entity(as[ChangeUserApiRequestADM]) { apiRequest =>
                requestContext =>

                    val userIri = stringFormatter.validateAndEscapeUserIri(value, throw BadRequestException(s"Invalid user IRI $value"))

                    if (userIri.equals(KnoraSystemInstances.Users.SystemUser.id) || userIri.equals(KnoraSystemInstances.Users.AnonymousUser.id)) {
                        throw BadRequestException("Changes to built-in users are not allowed.")
                    }

                    /* the api request is already checked at time of creation. see case class. */

                    val requestMessage: Future[UsersResponderRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield UserChangeSystemAdminMembershipStatusRequestADM(
                        userIri,
                        changeUserRequest = apiRequest,
                        requestingUser,
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

    private val updateUserSystemAdminMembershipFunction: ClientFunction =
        "updateUserSystemAdminMembership" description "Updates a user's SystemAdmin membership." params (
            "user" description "The user to be updated." paramType StoredUser
            ) doThis {
            httpPut(
                path = str("iri") / argMember("user", "id") / str("SystemAdmin"),
                body = Some(json("systemAdmin" -> argMember("user", "systemAdmin")))
            )
        } returns UserResponse

    private def updateUserSystemAdminMembershipTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("update-user-system-admin-membership-request"),
                text = SharedTestDataADM.changeUserSystemAdminMembershipRequest
            )
        )
    }

    /**
     * API MAY CHANGE: get user's project memberships
     */
    @ApiMayChange
    private def getUsersProjectMemberships: Route = path(UsersBasePath / "iri" / Segment / "project-memberships") { userIri =>
        get {
            requestContext =>
                val checkedUserIri = stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

                val requestMessage: Future[UserProjectMembershipsGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UserProjectMembershipsGetRequestADM(
                    userIri = checkedUserIri,
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

    private val getUserProjectMembershipsFunction: ClientFunction =
        "getUserProjectMemberships" description "Gets a user's project memberships." params (
            "iri" description "The IRI of the user." paramType UriDatatype
            ) doThis {
            httpGet(path = str("iri") / arg("iri") / str("project-memberships"))
        } returns ProjectsResponse

    private def getUserProjectMembershipsResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$UsersBasePathString/iri/$multiUserIriEnc/project-memberships") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-user-project-memberships-response"),
            text = responseStr
        )
    }

    /**
     * API MAY CHANGE: add user to project
     */
    @ApiMayChange
    private def addUserToProjectMembership: Route = path(UsersBasePath / "iri" / Segment / "project-memberships" / Segment) { (userIri, projectIri) =>
        post {
            requestContext =>
                val checkedUserIri = stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
                val checkedProjectIri = stringFormatter.validateAndEscapeProjectIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

                val requestMessage: Future[UserProjectMembershipAddRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UserProjectMembershipAddRequestADM(
                    userIri = checkedUserIri,
                    projectIri = checkedProjectIri,
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

    private val addUserToProjectMembershipFunction: ClientFunction =
        "addUserToProjectMembership" description "Adds a user to a project." params(
            "userIri" description "The user's IRI." paramType UriDatatype,
            "projectIri" description "The project's IRI." paramType UriDatatype
        ) doThis {
            httpPost(
                path = str("iri") / arg("userIri") / str("project-memberships") / arg("projectIri")
            )
        } returns UserResponse

    /**
     * API MAY CHANGE: remove user from project (and all groups belonging to this project)
     */
    @ApiMayChange
    private def removeUserFromProjectMembership: Route = path(UsersBasePath / "iri" / Segment / "project-memberships" / Segment) { (userIri, projectIri) =>

        delete {
            requestContext =>
                val checkedUserIri = stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
                val checkedProjectIri = stringFormatter.validateAndEscapeProjectIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

                val requestMessage: Future[UserProjectMembershipRemoveRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UserProjectMembershipRemoveRequestADM(
                    userIri = checkedUserIri,
                    projectIri = checkedProjectIri,
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

    private val removeUserFromProjectMembershipFunction: ClientFunction =
        "removeUserFromProjectMembership" description "Removes a user from a project." params(
            "userIri" description "The user's IRI." paramType UriDatatype,
            "projectIri" description "The project's IRI." paramType UriDatatype
        ) doThis {
            httpDelete(
                path = str("iri") / arg("userIri") / str("project-memberships") / arg("projectIri")
            )
        } returns UserResponse

    /**
     * API MAY CHANGE: get user's project admin memberships
     */
    @ApiMayChange
    private def getUsersProjectAdminMemberships: Route = path(UsersBasePath / "iri" / Segment / "project-admin-memberships") { userIri =>
        get {
            requestContext =>
                val checkedUserIri = stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

                val requestMessage: Future[UserProjectAdminMembershipsGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UserProjectAdminMembershipsGetRequestADM(
                    userIri = checkedUserIri,
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

    private val getUserProjectAdminMembershipsFunction: ClientFunction =
        "getUserProjectAdminMemberships" description "Gets a user's project admin memberships." params (
            "iri" description "The user's IRI." paramType UriDatatype
            ) doThis {
            httpGet(path = str("iri") / arg("iri") / str("project-admin-memberships"))
        } returns ProjectsResponse

    /**
     * API MAY CHANGE: add user to project admin
     */
    @ApiMayChange
    private def addUserToProjectAdminMembership: Route = path(UsersBasePath / "iri" / Segment / "project-admin-memberships" / Segment) { (userIri, projectIri) =>
        post {
            /*  */
            requestContext =>
                val checkedUserIri = stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
                val checkedProjectIri = stringFormatter.validateAndEscapeProjectIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

                val requestMessage: Future[UserProjectAdminMembershipAddRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UserProjectAdminMembershipAddRequestADM(
                    userIri = checkedUserIri,
                    projectIri = checkedProjectIri,
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

    private val addUserToProjectAdminMembershipFunction: ClientFunction =
        "addUserToProjectAdminMembership" description "Makes a user a project administrator." params(
            "userIri" description "The IRI of the user." paramType UriDatatype,
            "projectIri" description "The IRI of the project." paramType UriDatatype
        ) doThis {
            httpPost(
                path = str("iri") / arg("userIri") / str("project-admin-memberships") / arg("projectIri")
            )
        } returns UserResponse

    /**
     * API MAY CHANGE: remove user from project admin membership
     */
    @ApiMayChange
    private def removeUserFromProjectAdminMembership: Route = path(UsersBasePath / "iri" / Segment / "project-admin-memberships" / Segment) { (userIri, projectIri) =>
        delete {
            requestContext =>
                val checkedUserIri = stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
                val checkedProjectIri = stringFormatter.validateAndEscapeProjectIri(projectIri, throw BadRequestException(s"Invalid project IRI $projectIri"))

                val requestMessage: Future[UserProjectAdminMembershipRemoveRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UserProjectAdminMembershipRemoveRequestADM(
                    userIri = checkedUserIri,
                    projectIri = checkedProjectIri,
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

    private val removeUserFromProjectAdminMembershipFunction: ClientFunction =
        "removeUserFromProjectAdminMembership" description "Removes a user's project administrator status." params(
            "userIri" description "The IRI of the user." paramType UriDatatype,
            "projectIri" description "The IRI of the project." paramType UriDatatype
        ) doThis {
            httpDelete(
                path = str("iri") / arg("userIri") / str("project-admin-memberships") / arg("projectIri")
            )
        } returns UserResponse

    /**
     * API MAY CHANGE: get user's group memberships
     */
    @ApiMayChange
    private def getUsersGroupMemberships: Route = path(UsersBasePath / "iri" / Segment / "group-memberships") { userIri =>
        get {
            requestContext =>
                val checkedUserIri = stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

                val requestMessage: Future[UserGroupMembershipsGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UserGroupMembershipsGetRequestADM(
                    userIri = checkedUserIri,
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

    // #getUserGroupMembershipsFunction
    private val getUserGroupMembershipsFunction: ClientFunction =
        "getUserGroupMemberships" description "Gets a user's group memberships." params (
            "iri" description "The user's IRI." paramType UriDatatype
            ) doThis {
            httpGet(path = str("iri") / arg("iri") / str("group-memberships"))
        } returns GroupsResponse
    // #getUserGroupMembershipsFunction

    private def getUserGroupMembershipsTestResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$UsersBasePathString/iri/$anythingUser1IriEnc/group-memberships") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-user-group-memberships-response"),
            text = responseStr
        )
    }

    /**
     * API MAY CHANGE: add user to group
     */
    @ApiMayChange
    private def addUserToGroupMembership: Route = path(UsersBasePath / "iri" / Segment / "group-memberships" / Segment) { (userIri, groupIri) =>
        post {
            requestContext =>
                val checkedUserIri = stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
                val checkedGroupIri = stringFormatter.validateAndEscapeIri(groupIri, throw BadRequestException(s"Invalid group IRI $groupIri"))

                val requestMessage: Future[UserGroupMembershipAddRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UserGroupMembershipAddRequestADM(
                    userIri = checkedUserIri,
                    groupIri = checkedGroupIri,
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

    private val addUserToGroupMembershipFunction: ClientFunction =
        "addUserToGroupMembership" description "Adds a user to a group." params(
            "userIri" description "The IRI of the user." paramType UriDatatype,
            "groupIri" description "The IRI of the group." paramType UriDatatype
        ) doThis {
            httpPost(
                path = str("iri") / arg("userIri") / str("group-memberships") / arg("groupIri")
            )
        } returns UserResponse

    /**
     * API MAY CHANGE: remove user from group
     */
    @ApiMayChange
    private def removeUserFromGroupMembership: Route = path(UsersBasePath / "iri" / Segment / "group-memberships" / Segment) { (userIri, groupIri) =>
        delete {
            requestContext =>
                val checkedUserIri = stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
                val checkedGroupIri = stringFormatter.validateAndEscapeIri(groupIri, throw BadRequestException(s"Invalid group IRI $groupIri"))

                val requestMessage: Future[UserGroupMembershipRemoveRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield UserGroupMembershipRemoveRequestADM(
                    userIri = checkedUserIri,
                    groupIri = checkedGroupIri,
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

    private val removeUserFromGroupMembershipFunction: ClientFunction =
        "removeUserFromGroupMembership" description "Removes a user from a project." params(
            "userIri" description "The IRI of the user." paramType UriDatatype,
            "groupIri" description "The IRI of the group." paramType UriDatatype
        ) doThis {
            httpDelete(
                path = str("iri") / arg("userIri") / str("group-memberships") / arg("groupIri")
            )
        } returns UserResponse

    /**
     * The functions defined by this [[ClientEndpoint]].
     */
    override val functions: Seq[ClientFunction] = Seq(
        getUsersFunction,
        getUserFunction,
        getUserByIriFunction,
        getUserByEmailFunction,
        getUserByUsernameFunction,
        getUserGroupMembershipsFunction,
        getUserProjectMembershipsFunction,
        getUserProjectAdminMembershipsFunction,
        createUserFunction,
        updateUserBasicInformationFunction,
        updateUserStatusFunction,
        updateUserPasswordFunction,
        addUserToGroupMembershipFunction,
        removeUserFromGroupMembershipFunction,
        addUserToProjectMembershipFunction,
        removeUserFromProjectMembershipFunction,
        addUserToProjectAdminMembershipFunction,
        removeUserFromProjectAdminMembershipFunction,
        updateUserSystemAdminMembershipFunction,
        deleteUserFunction
    )

    /**
     * Returns test data for this endpoint.
     *
     * @return a set of test data files to be used for testing this endpoint.
     */
    override def getTestData(implicit executionContext: ExecutionContext, actorSystem: ActorSystem, materializer: ActorMaterializer): Future[Set[SourceCodeFileContent]] = {
        Future.sequence {
            Set(
                getUsersTestResponse,
                createUserTestRequest,
                getUserTestResponse,
                getUserGroupMembershipsTestResponse,
                updateUserTestRequest,
                updateUserPasswordTestRequest,
                updateUserStatusTestRequest,
                updateUserSystemAdminMembershipTestRequest,
                getUserProjectMembershipsResponse
            )
        }
    }
}
