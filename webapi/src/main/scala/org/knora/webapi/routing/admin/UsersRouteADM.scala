/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import io.swagger.annotations._
import org.knora.webapi.annotation.ApiMayChange
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}

import java.util.UUID
import javax.ws.rs.Path
import scala.concurrent.Future

object UsersRouteADM {
  val UsersBasePath: PathMatcher[Unit] = PathMatcher("admin" / "users")
}

/**
  * Provides an akka-http-routing function for API routes that deal with users.
  */
@Api(value = "users", produces = "application/json")
@Path("/admin/users")
class UsersRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

  import UsersRouteADM._

  /**
    * Returns the route.
    */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    getUsers(featureFactoryConfig) ~
      addUser(featureFactoryConfig) ~
      getUserByIri(featureFactoryConfig) ~
      getUserByEmail(featureFactoryConfig) ~
      getUserByUsername(featureFactoryConfig) ~
      changeUserBasicInformation(featureFactoryConfig) ~
      changeUserPassword(featureFactoryConfig) ~
      changeUserStatus(featureFactoryConfig) ~
      deleteUser(featureFactoryConfig) ~
      changeUserSystemAdminMembership(featureFactoryConfig) ~
      getUsersProjectMemberships(featureFactoryConfig) ~
      addUserToProjectMembership(featureFactoryConfig) ~
      removeUserFromProjectMembership(featureFactoryConfig) ~
      getUsersProjectAdminMemberships(featureFactoryConfig) ~
      addUserToProjectAdminMembership(featureFactoryConfig) ~
      removeUserFromProjectAdminMembership(featureFactoryConfig) ~
      getUsersGroupMemberships(featureFactoryConfig) ~
      addUserToGroupMembership(featureFactoryConfig) ~
      removeUserFromGroupMembership(featureFactoryConfig)
  @ApiOperation(value = "Get users", nickname = "getUsers", httpMethod = "GET", response = classOf[UsersGetResponseADM])
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    ))
  /* return all users */
  def getUsers(featureFactoryConfig: FeatureFactoryConfig): Route = path(UsersBasePath) {
    get { requestContext =>
      val requestMessage: Future[UsersGetRequestADM] = for {
        requestingUser <- getUserADM(
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig
        )
      } yield
        UsersGetRequestADM(
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

      RouteUtilADM.runJsonRoute(
        requestMessageF = requestMessage,
        requestContext = requestContext,
        featureFactoryConfig = featureFactoryConfig,
        settings = settings,
        responderManager = responderManager,
        log = log
      )
    }
  }

  @ApiOperation(value = "Add new user",
                nickname = "addUser",
                httpMethod = "POST",
                response = classOf[UserOperationResponseADM])
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body",
                           value = "\"user\" to create",
                           required = true,
                           dataTypeClass = classOf[CreateUserApiRequestADM],
                           paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    ))
  /* create a new user */
  def addUser(featureFactoryConfig: FeatureFactoryConfig): Route = path(UsersBasePath) {
    post {
      entity(as[CreateUserApiRequestADM]) { apiRequest => requestContext =>
        // get all values from request and make value objects from it
        //TODO use UserADMEntity (= UserADM with value objects) instead of UserEntity
        val user: UserEntity =
          UserEntity(
            id = stringFormatter.validateOptionalUserIri(apiRequest.id, throw BadRequestException(s"Invalid user IRI")),
            username = Username.create(apiRequest.username).fold(error => throw error, value => value),
            email = Email.create(apiRequest.email).fold(error => throw error, value => value),
            givenName = GivenName.create(apiRequest.givenName).fold(error => throw error, value => value),
            familyName = FamilyName.create(apiRequest.familyName).fold(error => throw error, value => value),
            password = Password.create(apiRequest.password).fold(error => throw error, value => value),
            status = Status.create(apiRequest.status).fold(error => throw error, value => value),
            lang = LanguageCode.create(apiRequest.lang).fold(error => throw error, value => value),
            systemAdmin = SystemAdmin.create(apiRequest.systemAdmin).fold(error => throw error, value => value)
          )
        val requestMessage: Future[UserCreateRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          UserCreateRequestADM(
            userEntity = user,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
      )
      }
    }
  }

  /**
    * return a single user identified by iri
    */
  private def getUserByIri(featureFactoryConfig: FeatureFactoryConfig): Route = path(UsersBasePath / "iri" / Segment) {
    value =>
      get { requestContext =>
        val requestMessage: Future[UserGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          UserGetRequestADM(
            identifier = UserIdentifierADM(maybeIri = Some(value)),
            userInformationTypeADM = UserInformationTypeADM.RESTRICTED,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
  }

  /**
    * return a single user identified by email
    */
  private def getUserByEmail(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "email" / Segment) { value =>
      get { requestContext =>
        val requestMessage: Future[UserGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          UserGetRequestADM(
            identifier = UserIdentifierADM(maybeEmail = Some(value)),
            userInformationTypeADM = UserInformationTypeADM.RESTRICTED,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
    * return a single user identified by username
    */
  private def getUserByUsername(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "username" / Segment) { value =>
      get { requestContext =>
        val requestMessage: Future[UserGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          UserGetRequestADM(
            identifier = UserIdentifierADM(maybeUsername = Some(value)),
            userInformationTypeADM = UserInformationTypeADM.RESTRICTED,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
    * API MAY CHANGE: Change existing user's basic information.
    */
  @ApiMayChange
  private def changeUserBasicInformation(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "BasicUserInformation") { value =>
      put {
        entity(as[ChangeUserApiRequestADM]) { apiRequest => requestContext =>
          val userIri =
            stringFormatter.validateAndEscapeUserIri(value, throw BadRequestException(s"Invalid user IRI $value"))

          if (userIri.equals(KnoraSystemInstances.Users.SystemUser.id) || userIri.equals(
                KnoraSystemInstances.Users.AnonymousUser.id)) {
            throw BadRequestException("Changes to built-in users are not allowed.")
          }

          /* the api request is already checked at time of creation. see case class. */

          val requestMessage: Future[UsersResponderRequestADM] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            UserChangeBasicUserInformationRequestADM(
              userIri = userIri,
              changeUserRequest = apiRequest,
              featureFactoryConfig = featureFactoryConfig,
              requestingUser = requestingUser,
              apiRequestID = UUID.randomUUID()
            )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
        }
      }
    }

  /**
    * API MAY CHANGE: Change user's password.
    */
  @ApiMayChange
  private def changeUserPassword(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "Password") { value =>
      put {
        entity(as[ChangeUserApiRequestADM]) { apiRequest => requestContext =>
          val userIri =
            stringFormatter.validateAndEscapeUserIri(value, throw BadRequestException(s"Invalid user IRI $value"))

          if (userIri.equals(KnoraSystemInstances.Users.SystemUser.id) || userIri.equals(
                KnoraSystemInstances.Users.AnonymousUser.id)) {
            throw BadRequestException("Changes to built-in users are not allowed.")
          }

          /* the api request is already checked at time of creation. see case class. */

          val requestMessage: Future[UsersResponderRequestADM] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            UserChangePasswordRequestADM(
              userIri = userIri,
              changeUserRequest = apiRequest,
              featureFactoryConfig = featureFactoryConfig,
              requestingUser = requestingUser,
              apiRequestID = UUID.randomUUID()
            )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
        }
      }
    }

  /**
    * API MAY CHANGE: Change user's status.
    */
  @ApiMayChange
  private def changeUserStatus(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "Status") { value =>
      put {
        entity(as[ChangeUserApiRequestADM]) { apiRequest => requestContext =>
          val userIri =
            stringFormatter.validateAndEscapeUserIri(value, throw BadRequestException(s"Invalid user IRI $value"))

          if (userIri.equals(KnoraSystemInstances.Users.SystemUser.id) || userIri.equals(
                KnoraSystemInstances.Users.AnonymousUser.id)) {
            throw BadRequestException("Changes to built-in users are not allowed.")
          }

          /* the api request is already checked at time of creation. see case class. */

          val requestMessage: Future[UsersResponderRequestADM] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            UserChangeStatusRequestADM(
              userIri = userIri,
              changeUserRequest = apiRequest,
              featureFactoryConfig = featureFactoryConfig,
              requestingUser = requestingUser,
              apiRequestID = UUID.randomUUID()
            )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
        }
      }
    }

  /**
    * API MAY CHANGE: delete a user identified by iri (change status to false).
    */
  @ApiMayChange
  private def deleteUser(featureFactoryConfig: FeatureFactoryConfig): Route = path(UsersBasePath / "iri" / Segment) {
    value =>
      delete { requestContext =>
        {
          val userIri =
            stringFormatter.validateAndEscapeUserIri(value, throw BadRequestException(s"Invalid user IRI $value"))

          if (userIri.equals(KnoraSystemInstances.Users.SystemUser.id) || userIri.equals(
                KnoraSystemInstances.Users.AnonymousUser.id)) {
            throw BadRequestException("Changes to built-in users are not allowed.")
          }

          /* update existing user's status to false */
          val requestMessage: Future[UserChangeStatusRequestADM] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            UserChangeStatusRequestADM(
              userIri = userIri,
              changeUserRequest = ChangeUserApiRequestADM(status = Some(false)),
              featureFactoryConfig = featureFactoryConfig,
              requestingUser = requestingUser,
              apiRequestID = UUID.randomUUID()
            )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
        }
      }
  }

  /**
    * API MAY CHANGE: Change user's SystemAdmin membership.
    */
  @ApiMayChange
  private def changeUserSystemAdminMembership(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "SystemAdmin") { value =>
      put {
        entity(as[ChangeUserApiRequestADM]) { apiRequest => requestContext =>
          val userIri =
            stringFormatter.validateAndEscapeUserIri(value, throw BadRequestException(s"Invalid user IRI $value"))

          if (userIri.equals(KnoraSystemInstances.Users.SystemUser.id) || userIri.equals(
                KnoraSystemInstances.Users.AnonymousUser.id)) {
            throw BadRequestException("Changes to built-in users are not allowed.")
          }

          /* the api request is already checked at time of creation. see case class. */

          val requestMessage: Future[UsersResponderRequestADM] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            UserChangeSystemAdminMembershipStatusRequestADM(
              userIri = userIri,
              changeUserRequest = apiRequest,
              featureFactoryConfig = featureFactoryConfig,
              requestingUser = requestingUser,
              apiRequestID = UUID.randomUUID()
            )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
        }
      }
    }

  /**
    * API MAY CHANGE: get user's project memberships
    */
  @ApiMayChange
  private def getUsersProjectMemberships(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "project-memberships") { userIri =>
      get { requestContext =>
        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        val requestMessage: Future[UserProjectMembershipsGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          UserProjectMembershipsGetRequestADM(
            userIri = checkedUserIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
    * API MAY CHANGE: add user to project
    */
  @ApiMayChange
  private def addUserToProjectMembership(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "project-memberships" / Segment) { (userIri, projectIri) =>
      post { requestContext =>
        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(projectIri,
                                                      throw BadRequestException(s"Invalid project IRI $projectIri"))

        val requestMessage: Future[UserProjectMembershipAddRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          UserProjectMembershipAddRequestADM(
            userIri = checkedUserIri,
            projectIri = checkedProjectIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
    * API MAY CHANGE: remove user from project (and all groups belonging to this project)
    */
  @ApiMayChange
  private def removeUserFromProjectMembership(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "project-memberships" / Segment) { (userIri, projectIri) =>
      delete { requestContext =>
        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(projectIri,
                                                      throw BadRequestException(s"Invalid project IRI $projectIri"))

        val requestMessage: Future[UserProjectMembershipRemoveRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          UserProjectMembershipRemoveRequestADM(
            userIri = checkedUserIri,
            projectIri = checkedProjectIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
    * API MAY CHANGE: get user's project admin memberships
    */
  @ApiMayChange
  private def getUsersProjectAdminMemberships(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "project-admin-memberships") { userIri =>
      get { requestContext =>
        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        val requestMessage: Future[UserProjectAdminMembershipsGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          UserProjectAdminMembershipsGetRequestADM(
            userIri = checkedUserIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
    * API MAY CHANGE: add user to project admin
    */
  @ApiMayChange
  private def addUserToProjectAdminMembership(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "project-admin-memberships" / Segment) { (userIri, projectIri) =>
      post {
        /*  */
        requestContext =>
          val checkedUserIri =
            stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
          val checkedProjectIri =
            stringFormatter.validateAndEscapeProjectIri(projectIri,
                                                        throw BadRequestException(s"Invalid project IRI $projectIri"))

          val requestMessage: Future[UserProjectAdminMembershipAddRequestADM] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield
            UserProjectAdminMembershipAddRequestADM(
              userIri = checkedUserIri,
              projectIri = checkedProjectIri,
              featureFactoryConfig = featureFactoryConfig,
              requestingUser = requestingUser,
              apiRequestID = UUID.randomUUID()
            )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
      }
    }

  /**
    * API MAY CHANGE: remove user from project admin membership
    */
  @ApiMayChange
  private def removeUserFromProjectAdminMembership(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "project-admin-memberships" / Segment) { (userIri, projectIri) =>
      delete { requestContext =>
        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(projectIri,
                                                      throw BadRequestException(s"Invalid project IRI $projectIri"))

        val requestMessage: Future[UserProjectAdminMembershipRemoveRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          UserProjectAdminMembershipRemoveRequestADM(
            userIri = checkedUserIri,
            projectIri = checkedProjectIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
    * API MAY CHANGE: get user's group memberships
    */
  @ApiMayChange
  private def getUsersGroupMemberships(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "group-memberships") { userIri =>
      get { requestContext =>
        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        val requestMessage: Future[UserGroupMembershipsGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          UserGroupMembershipsGetRequestADM(
            userIri = checkedUserIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
    * API MAY CHANGE: add user to group
    */
  @ApiMayChange
  private def addUserToGroupMembership(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "group-memberships" / Segment) { (userIri, groupIri) =>
      post { requestContext =>
        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
        val checkedGroupIri =
          stringFormatter.validateAndEscapeIri(groupIri, throw BadRequestException(s"Invalid group IRI $groupIri"))

        val requestMessage: Future[UserGroupMembershipAddRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          UserGroupMembershipAddRequestADM(
            userIri = checkedUserIri,
            groupIri = checkedGroupIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
    * API MAY CHANGE: remove user from group
    */
  @ApiMayChange
  private def removeUserFromGroupMembership(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "group-memberships" / Segment) { (userIri, groupIri) =>
      delete { requestContext =>
        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))
        val checkedGroupIri =
          stringFormatter.validateAndEscapeIri(groupIri, throw BadRequestException(s"Invalid group IRI $groupIri"))

        val requestMessage: Future[UserGroupMembershipRemoveRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield
          UserGroupMembershipRemoveRequestADM(
            userIri = checkedUserIri,
            groupIri = checkedGroupIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }
}
