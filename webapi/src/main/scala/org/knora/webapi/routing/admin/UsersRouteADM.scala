/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import io.swagger.annotations._
import org.knora.webapi.annotation.ApiMayChange
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM
import zio.prelude.Validation

import java.util.UUID
import javax.ws.rs.Path
import scala.concurrent.Future
import dsp.valueobjects.Iri.UserIri
import dsp.valueobjects.User._

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
    )
  )
  /* return all users */
  def getUsers(featureFactoryConfig: FeatureFactoryConfig): Route = path(UsersBasePath) {
    get { requestContext =>
      val requestMessage: Future[UsersGetRequestADM] = for {
        requestingUser <- getUserADM(
                            requestContext = requestContext,
                            featureFactoryConfig = featureFactoryConfig
                          )
      } yield UsersGetRequestADM(
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )

      RouteUtilADM.runJsonRoute(
        requestMessageF = requestMessage,
        requestContext = requestContext,
        featureFactoryConfig = featureFactoryConfig,
        settings = settings,
        appActor = appActor,
        log = log
      )
    }
  }

  @ApiOperation(
    value = "Add new user",
    nickname = "addUser",
    httpMethod = "POST",
    response = classOf[UserOperationResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"user\" to create",
        required = true,
        dataTypeClass = classOf[CreateUserApiRequestADM],
        paramType = "body"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  /* create a new user */
  def addUser(featureFactoryConfig: FeatureFactoryConfig): Route = path(UsersBasePath) {
    post {
      entity(as[CreateUserApiRequestADM]) { apiRequest => requestContext =>
        // get all values from request and make value objects from it
        val id: Validation[Throwable, Option[UserIri]]        = UserIri.make(apiRequest.id)
        val username: Validation[Throwable, Username]         = Username.make(apiRequest.username)
        val email: Validation[Throwable, Email]               = Email.make(apiRequest.email)
        val givenName: Validation[Throwable, GivenName]       = GivenName.make(apiRequest.givenName)
        val familyName: Validation[Throwable, FamilyName]     = FamilyName.make(apiRequest.familyName)
        val password: Validation[Throwable, Password]         = Password.make(apiRequest.password)
        val status: Validation[Throwable, UserStatus]         = UserStatus.make(apiRequest.status)
        val languageCode: Validation[Throwable, LanguageCode] = LanguageCode.make(apiRequest.lang)
        val systemAdmin: Validation[Throwable, SystemAdmin]   = SystemAdmin.make(apiRequest.systemAdmin)

        val validatedUserCreatePayload: Validation[Throwable, UserCreatePayloadADM] =
          Validation.validateWith(
            id,
            username,
            email,
            givenName,
            familyName,
            password,
            status,
            languageCode,
            systemAdmin
          )(UserCreatePayloadADM)

        val requestMessage: Future[UserCreateRequestADM] = for {
          payload        <- toFuture(validatedUserCreatePayload)
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)
        } yield UserCreateRequestADM(
          userCreatePayloadADM = payload,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }
  }

  /**
   * return a single user identified by iri
   */
  private def getUserByIri(featureFactoryConfig: FeatureFactoryConfig): Route = path(UsersBasePath / "iri" / Segment) {
    userIri =>
      get { requestContext =>
        val requestMessage: Future[UserGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              featureFactoryConfig = featureFactoryConfig
                            )
        } yield UserGetRequestADM(
          identifier = UserIdentifierADM(maybeIri = Some(userIri)),
          userInformationTypeADM = UserInformationTypeADM.Restricted,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
  }

  /**
   * return a single user identified by email
   */
  private def getUserByEmail(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "email" / Segment) { userIri =>
      get { requestContext =>
        val requestMessage: Future[UserGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              featureFactoryConfig = featureFactoryConfig
                            )
        } yield UserGetRequestADM(
          identifier = UserIdentifierADM(maybeEmail = Some(userIri)),
          userInformationTypeADM = UserInformationTypeADM.Restricted,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * return a single user identified by username
   */
  private def getUserByUsername(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "username" / Segment) { userIri =>
      get { requestContext =>
        val requestMessage: Future[UserGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              featureFactoryConfig = featureFactoryConfig
                            )
        } yield UserGetRequestADM(
          identifier = UserIdentifierADM(maybeUsername = Some(userIri)),
          userInformationTypeADM = UserInformationTypeADM.Restricted,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * API MAY CHANGE: Change existing user's basic information.
   */
  @ApiMayChange
  private def changeUserBasicInformation(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "BasicUserInformation") { userIri =>
      put {
        entity(as[ChangeUserApiRequestADM]) { apiRequest => requestContext =>
          if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

          val checkedUserIri =
            stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

          if (
            checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
              KnoraSystemInstances.Users.AnonymousUser.id
            )
          ) {
            throw BadRequestException("Changes to built-in users are not allowed.")
          }

          val maybeUsername: Option[Username]   = Username.make(apiRequest.username).fold(e => throw e.head, v => v)
          val maybeEmail: Option[Email]         = Email.make(apiRequest.email).fold(e => throw e.head, v => v)
          val maybeGivenName: Option[GivenName] = GivenName.make(apiRequest.givenName).fold(e => throw e.head, v => v)
          val maybeFamilyName: Option[FamilyName] =
            FamilyName.make(apiRequest.familyName).fold(e => throw e.head, v => v)
          val maybeLanguageCode: Option[LanguageCode] =
            LanguageCode.make(apiRequest.lang).fold(e => throw e.head, v => v)

          val userUpdatePayload: UserUpdateBasicInformationPayloadADM =
            UserUpdateBasicInformationPayloadADM(
              maybeUsername,
              maybeEmail,
              maybeGivenName,
              maybeFamilyName,
              maybeLanguageCode
            )

          /* the api request is already checked at time of creation. see case class. */
          val requestMessage: Future[UsersResponderRequestADM] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext,
                                featureFactoryConfig = featureFactoryConfig
                              )
          } yield UserChangeBasicInformationRequestADM(
            userIri = checkedUserIri,
            userUpdateBasicInformationPayload = userUpdatePayload,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            appActor = appActor,
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
    path(UsersBasePath / "iri" / Segment / "Password") { userIri =>
      put {
        entity(as[ChangeUserPasswordApiRequestADM]) { apiRequest => requestContext =>
          if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

          val checkedUserIri =
            stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

          if (
            checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
              KnoraSystemInstances.Users.AnonymousUser.id
            )
          ) {
            throw BadRequestException("Changes to built-in users are not allowed.")
          }

          val requesterPassword = apiRequest.requesterPassword match {
            case Some(password) => Password.make(password).fold(e => throw e.head, v => v)
            case None           => throw BadRequestException("The requester's password is missing.")
          }
          val changedPassword = apiRequest.newPassword match {
            case Some(password) => Password.make(password).fold(e => throw e.head, v => v)
            case None           => throw BadRequestException("The new password is missing.")
          }

          val requestMessage: Future[UsersResponderRequestADM] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext,
                                featureFactoryConfig = featureFactoryConfig
                              )
          } yield UserChangePasswordRequestADM(
            userIri = checkedUserIri,
            userUpdatePasswordPayload = UserUpdatePasswordPayloadADM(requesterPassword, changedPassword),
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            appActor = appActor,
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
    path(UsersBasePath / "iri" / Segment / "Status") { userIri =>
      put {
        entity(as[ChangeUserApiRequestADM]) { apiRequest => requestContext =>
          if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

          val checkedUserIri =
            stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

          if (
            checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
              KnoraSystemInstances.Users.AnonymousUser.id
            )
          ) {
            throw BadRequestException("Changes to built-in users are not allowed.")
          }

          val newStatus = apiRequest.status match {
            case Some(status) => UserStatus.make(status).fold(error => throw error.head, value => value)
            case None         => throw BadRequestException("The status is missing.")
          }

          val requestMessage: Future[UsersResponderRequestADM] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext,
                                featureFactoryConfig = featureFactoryConfig
                              )
          } yield UserChangeStatusRequestADM(
            userIri = checkedUserIri,
            status = newStatus,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            appActor = appActor,
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
    userIri =>
      delete { requestContext =>
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        if (
          checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
            KnoraSystemInstances.Users.AnonymousUser.id
          )
        ) {
          throw BadRequestException("Changes to built-in users are not allowed.")
        }

        /* update existing user's status to false */
        val status = UserStatus.make(false).fold(error => throw error.head, value => value)

        val requestMessage: Future[UserChangeStatusRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              featureFactoryConfig = featureFactoryConfig
                            )
        } yield UserChangeStatusRequestADM(
          userIri = checkedUserIri,
          status = status,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
  }

  /**
   * API MAY CHANGE: Change user's SystemAdmin membership.
   */
  @ApiMayChange
  private def changeUserSystemAdminMembership(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(UsersBasePath / "iri" / Segment / "SystemAdmin") { userIri =>
      put {
        entity(as[ChangeUserApiRequestADM]) { apiRequest => requestContext =>
          if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

          val checkedUserIri =
            stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

          if (
            checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
              KnoraSystemInstances.Users.AnonymousUser.id
            )
          ) {
            throw BadRequestException("Changes to built-in users are not allowed.")
          }

          val newSystemAdmin = apiRequest.systemAdmin match {
            case Some(systemAdmin) => SystemAdmin.make(systemAdmin).fold(e => throw e.head, v => v)
            case None              => throw BadRequestException("The systemAdmin is missing.")
          }

          val requestMessage: Future[UsersResponderRequestADM] = for {
            requestingUser <- getUserADM(
                                requestContext = requestContext,
                                featureFactoryConfig = featureFactoryConfig
                              )
          } yield UserChangeSystemAdminMembershipStatusRequestADM(
            userIri = checkedUserIri,
            systemAdmin = newSystemAdmin,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            appActor = appActor,
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
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        val requestMessage: Future[UserProjectMembershipsGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              featureFactoryConfig = featureFactoryConfig
                            )
        } yield UserProjectMembershipsGetRequestADM(
          userIri = checkedUserIri,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          appActor = appActor,
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
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        if (
          checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
            KnoraSystemInstances.Users.AnonymousUser.id
          )
        ) {
          throw BadRequestException("Changes to built-in users are not allowed.")
        }

        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(
            projectIri,
            throw BadRequestException(s"Invalid project IRI $projectIri")
          )

        val requestMessage: Future[UserProjectMembershipAddRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              featureFactoryConfig = featureFactoryConfig
                            )
        } yield UserProjectMembershipAddRequestADM(
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
          appActor = appActor,
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
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        if (
          checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
            KnoraSystemInstances.Users.AnonymousUser.id
          )
        ) {
          throw BadRequestException("Changes to built-in users are not allowed.")
        }

        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(
            projectIri,
            throw BadRequestException(s"Invalid project IRI $projectIri")
          )

        val requestMessage: Future[UserProjectMembershipRemoveRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              featureFactoryConfig = featureFactoryConfig
                            )
        } yield UserProjectMembershipRemoveRequestADM(
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
          appActor = appActor,
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
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        val requestMessage: Future[UserProjectAdminMembershipsGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              featureFactoryConfig = featureFactoryConfig
                            )
        } yield UserProjectAdminMembershipsGetRequestADM(
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
          appActor = appActor,
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
      post { requestContext =>
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        if (
          checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
            KnoraSystemInstances.Users.AnonymousUser.id
          )
        ) {
          throw BadRequestException("Changes to built-in users are not allowed.")
        }

        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(
            projectIri,
            throw BadRequestException(s"Invalid project IRI $projectIri")
          )

        val requestMessage: Future[UserProjectAdminMembershipAddRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              featureFactoryConfig = featureFactoryConfig
                            )
        } yield UserProjectAdminMembershipAddRequestADM(
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
          appActor = appActor,
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
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        if (
          checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
            KnoraSystemInstances.Users.AnonymousUser.id
          )
        ) {
          throw BadRequestException("Changes to built-in users are not allowed.")
        }

        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(
            projectIri,
            throw BadRequestException(s"Invalid project IRI $projectIri")
          )

        val requestMessage: Future[UserProjectAdminMembershipRemoveRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              featureFactoryConfig = featureFactoryConfig
                            )
        } yield UserProjectAdminMembershipRemoveRequestADM(
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
          appActor = appActor,
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
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        val requestMessage: Future[UserGroupMembershipsGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              featureFactoryConfig = featureFactoryConfig
                            )
        } yield UserGroupMembershipsGetRequestADM(
          userIri = checkedUserIri,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          appActor = appActor,
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
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        if (
          checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
            KnoraSystemInstances.Users.AnonymousUser.id
          )
        ) {
          throw BadRequestException("Changes to built-in users are not allowed.")
        }

        val checkedGroupIri =
          stringFormatter.validateAndEscapeIri(groupIri, throw BadRequestException(s"Invalid group IRI $groupIri"))

        val requestMessage: Future[UserGroupMembershipAddRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              featureFactoryConfig = featureFactoryConfig
                            )
        } yield UserGroupMembershipAddRequestADM(
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
          appActor = appActor,
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
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        if (
          checkedUserIri.equals(KnoraSystemInstances.Users.SystemUser.id) || checkedUserIri.equals(
            KnoraSystemInstances.Users.AnonymousUser.id
          )
        ) {
          throw BadRequestException("Changes to built-in users are not allowed.")
        }

        val checkedGroupIri =
          stringFormatter.validateAndEscapeIri(groupIri, throw BadRequestException(s"Invalid group IRI $groupIri"))

        val requestMessage: Future[UserGroupMembershipRemoveRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              featureFactoryConfig = featureFactoryConfig
                            )
        } yield UserGroupMembershipRemoveRequestADM(
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
          appActor = appActor,
          log = log
        )
      }
    }
}
