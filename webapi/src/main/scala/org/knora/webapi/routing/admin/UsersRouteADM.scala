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
import akka.http.scaladsl.server.Route
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi.annotation.ApiMayChange
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.clientapi._
import org.knora.webapi.{BadRequestException, KnoraSystemInstances, OntologyConstants}

import scala.concurrent.Future


/**
  * Provides a akka-http-routing function for API routes that deal with users.
  */

@Api(value = "users", produces = "application/json")
@Path("/admin/users")
class UsersRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with ClientEndpoint {
    import EndpointFunctionDSL._

    /**
      * The name of this [[ClientEndpoint]].
      */
    override val name: String = "UsersEndpoint"

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
    private val Group = classRef(OntologyConstants.KnoraAdminV2.GroupClass.toSmartIri)
    private val Project = classRef(OntologyConstants.KnoraAdminV2.ProjectClass.toSmartIri)


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
    def getUsers: Route = path("admin" / "users") {
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
            httpGet(emptyPath)
        } returns UsersResponse


    @ApiOperation(value = "Add new user", nickname = "addUser", httpMethod = "POST", response = classOf[UserOperationResponseADM])
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"user\" to create", required = true,
            dataTypeClass = classOf[CreateUserApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /* create a new user */
    def addUser: Route = path("admin" / "users") {
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
                path = emptyPath,
                body = Some(arg("user"))
            )
        } returns UserResponse
    // #createUserFunction

    /**
      * return a single user identified by iri
      */
    private def getUserByIri: Route = path("admin" / "users" / "iri" / Segment) { value =>
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
            "value" description "The value of the property by which the user is identified." paramType StringLiteral
        ) doThis {
            httpGet(arg("property") / arg("value"))
        } returns UserResponse
    // #getUserFunction

    // #getUserByIriFunction
    private val getUserByIriFunction: ClientFunction =
        "getUserByIri" description "Gets a user by IRI." params (
            "iri" description "The IRI of the user." paramType StringLiteral
            ) doThis {
            getUserFunction withArgs(str("iri"), arg("iri"))
        } returns UserResponse
    // #getUserByIriFunction

    /**
      * return a single user identified by email
      */
    private def getUserByEmail: Route = path("admin" / "users" / "email" / Segment) { value =>
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
            "email" description "The email address of the user." paramType StringLiteral
            ) doThis {
            getUserFunction withArgs(str("email"), arg("email"))
        } returns UserResponse

    /**
      * return a single user identified by username
      */
    private def getUserByUsername: Route = path("admin" / "users" / "username" / Segment) { value =>
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
            "username" description "The username of the user." paramType StringLiteral
            ) doThis {
            getUserFunction withArgs(str("username"), arg("username"))
        } returns UserResponse

    /**
      * API MAY CHANGE: Change existing user's basic information.
      */
    @ApiMayChange
    private def changeUserBasicInformation: Route = path("admin" / "users" / "iri" / Segment / "BasicUserInformation") { value =>
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

    // #updateUserBasicInformationFunction
    private val updateUserBasicInformationFunction: ClientFunction =
        "updateUserBasicInformation" description "Updates an existing user's basic information." params (
            "user" description "The user to be updated." paramType User
            ) doThis {
            httpPut(
                path = str("iri") / argMember("user", "id") / str("BasicUserInformation"),
                body = Some(json(
                    "username" -> argMember("user", "username"),
                    "email" -> argMember("user", "email"),
                    "givenName" -> argMember("user", "givenName"),
                    "familyName" -> argMember("user", "familyName"),
                    "lang" -> argMember("user", "lang")
                ))
            )
        } returns UserResponse
    // #updateUserBasicInformationFunction

    /**
      * API MAY CHANGE: Change user's password.
      */
    @ApiMayChange
    private def changeUserPassword: Route = path("admin" / "users" / "iri" / Segment / "Password") { value =>
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

    private val updateUserPasswordFunction: ClientFunction =
        "updateUserPassword" description "Updates a user's password." params(
            "user" description "The user to be updated." paramType User,
            "oldPassword" description "The user's old password." paramType StringLiteral,
            "newPassword" description "The user's new password." paramType StringLiteral
        ) doThis {
            httpPut(
                path = str("iri") / argMember("user", "id") / str("Password"),
                body = Some(json(
                    "requesterPassword" -> arg("oldPassword"),
                    "newPassword" -> arg("newPassword")
                ))
            )
        } returns UserResponse

    /**
      * API MAY CHANGE: Change user's status.
      */
    @ApiMayChange
    private def changeUserStatus: Route = path("admin" / "users" / "iri" / Segment / "Status") { value =>
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
            "user" description "The user to be updated." paramType User
            ) doThis {
            httpPut(
                path = str("iri") / argMember("user", "id") / str("Status"),
                body = Some(json("status" -> argMember("user", "status")))
            )
        } returns UserResponse

    /**
      * API MAY CHANGE: delete a user identified by iri (change status to false).
      */
    @ApiMayChange
    private def deleteUser: Route = path("admin" / "users" / "iri" / Segment) { value =>
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
            "user" description "The user to be deleted." paramType User
            ) doThis {
            httpDelete(
                path = str("iri") / argMember("user", "id")
            )
        } returns UserResponse

    /**
      * API MAY CHANGE: Change user's SystemAdmin membership.
      */
    @ApiMayChange
    private def changeUserSytemAdminMembership: Route = path("admin" / "users" / "iri" / Segment / "SystemAdmin") { value =>
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
        "updateUserSystemAdminMembership" description "Updates a user's SystemAdmin membership." params(
            "user" description "The user to be updated." paramType User
            ) doThis {
            httpPut(
                path = str("iri") / argMember("user", "id") / str("SystemAdmin"),
                body = Some(json("systemAdmin" -> argMember("user", "systemAdmin")))
            )
        } returns UserResponse

    /**
      * API MAY CHANGE: get user's project memberships
      */
    @ApiMayChange
    private def getUsersProjectMemberships: Route = path("admin" / "users" / "iri" / Segment / "project-memberships") { userIri =>
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
            "user" description "The user whose project memberships are to be retrieved." paramType User
            ) doThis {
            httpGet(path = str("iri") / argMember("user", "id") / str("project-memberships"))
        } returns ProjectsResponse

    /**
      * API MAY CHANGE: add user to project
      */
    @ApiMayChange
    private def addUserToProjectMembership: Route = path("admin" / "users" / "iri" / Segment / "project-memberships" / Segment) { (userIri, projectIri) =>
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
            "user" description "The user to be added to the project." paramType User,
            "project" description "The project to which the user should be added." paramType Project
        ) doThis {
            httpPost(
                path = str("iri") / argMember("user", "id") / str("project-memberships") / argMember("project", "id")
            )
        } returns UserResponse

    /**
      * API MAY CHANGE: remove user from project (and all groups belonging to this project)
      */
    @ApiMayChange
    private def removeUserFromProjectMembership: Route = path("admin" / "users" / "iri" / Segment / "project-memberships" / Segment) { (userIri, projectIri) =>

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
            "user" description "The user to be removed from the project." paramType User,
            "project" description "The project from which the user should be removed." paramType Project
        ) doThis {
            httpDelete(
                path = str("iri") / argMember("user", "id") / str("project-memberships") / argMember("project", "id")
            )
        } returns UserResponse

    /**
      * API MAY CHANGE: get user's project admin memberships
      */
    @ApiMayChange
    private def getUsersProjectAdminMemberships: Route = path("admin" / "users" / "iri" / Segment / "project-admin-memberships") { userIri =>
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
            "user" description "The user whose project admin memberships are to be retrieved." paramType User
            ) doThis {
            httpGet(path = str("iri") / argMember("user", "id") / str("project-admin-memberships"))
        } returns ProjectsResponse

    /**
      * API MAY CHANGE: add user to project admin
      */
    @ApiMayChange
    private def addUserToProjectAdminMembership: Route = path("admin" / "users" / "iri" / Segment / "project-admin-memberships" / Segment) { (userIri, projectIri) =>
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
            "user" description "The user to be made administrator of a project." paramType User,
            "project" description "The project in which the user should become a project administrator." paramType Project
        ) doThis {
            httpPost(
                path = str("iri") / argMember("user", "id") / str("project-admin-memberships") / argMember("project", "id")
            )
        } returns UserResponse

    /**
      * API MAY CHANGE: remove user from project admin membership
      */
    @ApiMayChange
    private def removeUserFromProjectAdminMembership: Route = path("admin" / "users" / "iri" / Segment / "project-admin-memberships" / Segment) { (userIri, projectIri) =>
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
            "user" description "The user whose project administrator status should be removed." paramType User,
            "project" description "The project in which the user should no longer be a project administrator." paramType Project
        ) doThis {
            httpDelete(
                path = str("iri") / argMember("user", "id") / str("project-admin-memberships") / argMember("project", "id")
            )
        } returns UserResponse

    /**
      * API MAY CHANGE: get user's group memberships
      */
    @ApiMayChange
    private def getUsersGroupMemberships: Route = path("admin" / "users" / "iri" / Segment / "group-memberships") { userIri =>
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
            "user" description "The user whose group memberships are to be retrieved." paramType User
            ) doThis {
            httpGet(path = str("iri") / argMember("user", "id") / str("group-memberships"))
        } returns GroupsResponse
    // #getUserGroupMembershipsFunction

    /**
      * API MAY CHANGE: add user to group
      */
    @ApiMayChange
    private def addUserToGroupMembership: Route = path("admin" / "users" / "iri" / Segment / "group-memberships" / Segment) { (userIri, groupIri) =>
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
            "user" description "The user to be added to the group." paramType User,
            "group" description "The group to which the user should be added." paramType Group
        ) doThis {
            httpPost(
                path = str("iri") / argMember("user", "id") / str("group-memberships") / argMember("group", "id")
            )
        } returns UserResponse

    /**
      * API MAY CHANGE: remove user from group
      */
    @ApiMayChange
    private def removeUserFromGroupMembership: Route = path("admin" / "users" / "iri" / Segment / "group-memberships" / Segment) { (userIri, groupIri) =>
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
            "user" description "The user to be removed from the project." paramType User,
            "group" description "The group from which the user should be removed." paramType Group
        ) doThis {
            httpDelete(
                path = str("iri") / argMember("user", "id") / str("group-memberships") / argMember("group", "id")
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
}
