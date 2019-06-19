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
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.clientapi._
import org.knora.webapi.{BadRequestException, KnoraSystemInstances, OntologyConstants}

import scala.concurrent.Future



/**
  * Provides a akka-http-routing function for API routes that deal with users.
  */

@Api(value = "users", produces = "application/json")
@Path("/admin/users")
class UsersRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {


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


    /**
      *  return a single user identified by iri
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

    /**
      *  return a single user identified by email
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

    /**
      *  return a single user identified by username
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


    /**
      *  API MAY CHANGE: get user's project memberships
      */
    @ApiMayChange
    private def getUsersProjectMemberships: Route = path("admin" / "users" / "iri" / Segment / "project-memberships" ) { userIri =>
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

    /**
      *  API MAY CHANGE: remove user from project (and all groups belonging to this project)
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

    /**
      * API MAY CHANGE: get user's group memberships
      */
    @ApiMayChange
    private def getUsersGroupMemberships: Route = path("admin" / "users" / "iri" / Segment / "group-memberships" ) { userIri =>
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
}

/**
  * A definition of the users route for generating client library code.
  */
class UsersEndpoint extends ClientEndpoint {
    implicit private val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val usersResponseClassRef = ClientClassReference(
        className = "UsersResponse",
        classIri = OntologyConstants.KnoraAdminV2.UsersResponse.toSmartIri
    )

    private val userResponseClassRef = ClientClassReference(
        className = "UserResponse",
        classIri = OntologyConstants.KnoraAdminV2.UserResponse.toSmartIri
    )

    private val userClassRef = ClientClassReference(
        className = "User",
        classIri = OntologyConstants.KnoraAdminV2.UserClass.toSmartIri
    )

    private val getUsers = ClientFunction(
        name = "getUsers",
        params = Seq.empty,
        returnType = usersResponseClassRef,
        implementation = ClientHttpRequest(
            httpMethod = GET,
            urlPath = Seq(StringLiteralValue(""))
        ),
        description = "Returns a list of all users."
    )

    private val getUser = ClientFunction(
        name = "getUser",
        params = Seq(
            FunctionParam(
                name = "property",
                objectType = ClientEnumLiteral(
                    possibleValues = Set("iri", "email", "username")
                ),
                description = "The name of the property by which the user is identified."
            ),
            FunctionParam(
                name = "value",
                objectType = ClientStringLiteral,
                description = "The value of the property by which the user is identified."
            )
        ),
        returnType = usersResponseClassRef,
        implementation = ClientHttpRequest(
            httpMethod = GET,
            urlPath = Seq(
                SlashUrlComponent,
                ArgValue("property"),
                SlashUrlComponent,
                ArgValue("value")
            )
        ),
        description = "Gets a user by a property."
    )

    private val getUserByIri = ClientFunction(
        name = "getUserByIri",
        params = Seq(
            FunctionParam(
                name = "iri",
                objectType = ClientStringLiteral,
                description = "The IRI of the user."
            )
        ),
        returnType = userResponseClassRef,
        implementation = FunctionCall(
            name = "getUser",
            args = Seq(
                StringLiteralValue("iri"),
                ArgValue("iri")
            )
        ),
        description = "Gets a user by IRI."
    )

    private val getUserByEmail = ClientFunction(
        name = "getUserByEmail",
        params = Seq(
            FunctionParam(
                name = "email",
                objectType = ClientStringLiteral,
                description = "The email address of the user."
            )
        ),
        returnType = userResponseClassRef,
        implementation = FunctionCall(
            name = "getUser",
            args = Seq(
                StringLiteralValue("email"),
                ArgValue("email")
            )
        ),
        description = "Gets a user by email address."
    )

    private val getUserByUsername = ClientFunction(
        name = "getUserByUsername",
        params = Seq(
            FunctionParam(
                name = "username",
                objectType = ClientStringLiteral,
                description = "The username of the user."
            )
        ),
        returnType = userResponseClassRef,
        implementation = FunctionCall(
            name = "getUser",
            args = Seq(
                StringLiteralValue("username"),
                ArgValue("username")
            )
        ),
        description = "Gets a user by username."
    )

    private val createUser = ClientFunction(
        name = "createUser",
        params = Seq(
            FunctionParam(
                name = "user",
                objectType = userClassRef,
                description = "The user to be created."
            )
        ),
        returnType = userResponseClassRef,
        implementation = ClientHttpRequest(
            httpMethod = POST,
            urlPath = Seq.empty,
            requestBody = Some(ArgValue("user"))
        ),
        description = "Creates a user."
    )

    private val updateUser = ClientFunction(
        name = "updateUser",
        params = Seq(
            FunctionParam(
                name = "user",
                objectType = userClassRef,
                description = "The user to be updated."
            )
        ),
        returnType = userResponseClassRef,
        implementation = ClientHttpRequest(
            httpMethod = PUT,
            urlPath = Seq.empty,
            requestBody = Some(ArgValue("user"))
        ),
        description = "Updates a user."
    )

    private val updateUserStatus = ClientFunction(
        name = "updateUserStatus",
        params = Seq(
            FunctionParam(
                name = "user",
                objectType = userClassRef,
                description = "The user to be updated."
            )
        ),
        returnType = userResponseClassRef,
        implementation = ClientHttpRequest(
            httpMethod = PUT,
            urlPath = Seq(
                StringLiteralValue("/iri/"),
                ArgValue(
                    name = "user",
                    memberVariableName = Some("id")
                ),
                StringLiteralValue("/Status")
            ),
            requestBody = Some(
                JsonRequestBody(
                    Map(
                        "status" -> ArgValue(
                            name = "user",
                            memberVariableName = Some("status")
                        )
                    )
                )
            )
        ),
        description = "Updates a user's status."
    )

    private val updateUserPassword = ClientFunction(
        name = "updateUserPassword",
        params = Seq(
            FunctionParam(
                name = "user",
                objectType = userClassRef,
                description = "The user to be updated."
            ),
            FunctionParam(
                name = "oldPassword",
                objectType = ClientStringLiteral,
                description = "The user's old password."
            ),
            FunctionParam(
                name = "newPassword",
                objectType = ClientStringLiteral,
                description = "The user's new password."
            )
        ),
        returnType = userResponseClassRef,
        implementation = ClientHttpRequest(
            httpMethod = PUT,
            urlPath = Seq(
                StringLiteralValue("/iri/"),
                ArgValue(
                    name = "user",
                    memberVariableName = Some("id")
                ),
                StringLiteralValue("/Password")
            ),
            requestBody = Some(
                JsonRequestBody(
                    Map(
                        "requesterPassword" -> ArgValue(
                            name = "oldPassword"
                        ),
                        "newPassword" -> ArgValue(
                            name = "newPassword"
                        )
                    )
                )
            )
        ),
        description = "Updates a user's password."
    )

    private val deleteUser = ClientFunction(
        name = "deleteUser",
        params = Seq(
            FunctionParam(
                name = "user",
                objectType = userClassRef,
                description = "The user to be deleted."
            )
        ),
        returnType = userResponseClassRef,
        implementation = ClientHttpRequest(
            httpMethod = PUT,
            urlPath = Seq(
                StringLiteralValue("/iri/"),
                ArgValue(
                    name = "user",
                    memberVariableName = Some("id")
                ),
                StringLiteralValue("/Status")
            ),
            requestBody = Some(
                JsonRequestBody(
                    Map(
                        "status" -> BooleanLiteralValue(false)
                    )
                )
            )
        ),
        description = "Deletes a user. This method does not actually delete a user, but sets the status to false."
    )

    override val functions: Seq[ClientFunction] = Seq(
        getUsers,
        getUser,
        getUserByIri,
        getUserByEmail,
        getUserByUsername,
        createUser,
        updateUser,
        updateUserStatus,
        updateUserPassword,
        deleteUser
    )

    override val name: String = "UsersEndpoint"

    override val urlPath: String = "/users"

    override val description: String = "An endpoint for working with Knora users."
}
