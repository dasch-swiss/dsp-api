/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Route
import zio._
import zio.prelude.Validation

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri.UserIri
import dsp.valueobjects.LanguageCode
import dsp.valueobjects.User._
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM
import org.knora.webapi.routing.RouteUtilADM.getIriUser
import org.knora.webapi.routing.RouteUtilADM.getIriUserUuid
import org.knora.webapi.routing.RouteUtilADM.getUserUuid
import org.knora.webapi.routing.RouteUtilADM.runJsonRouteZ
import org.knora.webapi.routing.RouteUtilZ

/**
 * Provides an akka-http-routing function for API routes that deal with users.
 */
final case class UsersRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator with StringFormatter with MessageRelay]
) extends KnoraRoute(routeData, runtime) {

  private val usersBasePath: PathMatcher[Unit] = PathMatcher("admin" / "users")

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    getUsers() ~
      addUser() ~
      getUserByIri() ~
      getUserByEmail() ~
      getUserByUsername() ~
      changeUserBasicInformation() ~
      changeUserPassword() ~
      changeUserStatus() ~
      deleteUser() ~
      changeUserSystemAdminMembership() ~
      getUsersProjectMemberships() ~
      addUserToProjectMembership() ~
      removeUserFromProjectMembership() ~
      getUsersProjectAdminMemberships() ~
      addUserToProjectAdminMembership() ~
      removeUserFromProjectAdminMembership() ~
      getUsersGroupMemberships() ~
      addUserToGroupMembership() ~
      removeUserFromGroupMembership()

  /* return all users */
  def getUsers(): Route = path(usersBasePath) {
    get { ctx =>
      runJsonRouteZ(Authenticator.getUserADM(ctx).map(user => UsersGetRequestADM(requestingUser = user)), ctx)
    }
  }

  /* create a new user */
  private def addUser(): Route = path(usersBasePath) {
    post {
      entity(as[CreateUserApiRequestADM]) { apiRequest => ctx =>
        val requestTask = for {
          payload        <- UserCreatePayloadADM.make(apiRequest).mapError(BadRequestException(_)).toZIO
          requestingUser <- Authenticator.getUserADM(ctx)
          apiRequestId   <- RouteUtilZ.randomUuid()
        } yield UserCreateRequestADM(payload, requestingUser, apiRequestId)
        runJsonRouteZ(requestTask, ctx)
      }
    }
  }

  /**
   * return a single user identified by iri
   */
  private def getUserByIri(): Route =
    path(usersBasePath / "iri" / Segment)(userIri => get(getUser(UserIdentifierADM(maybeIri = Some(userIri)), _)))

  private def getUser(id: UserIdentifierADM, ctx: RequestContext) = {
    val task = Authenticator.getUserADM(ctx).map(UserGetRequestADM(id, UserInformationTypeADM.Restricted, _))
    runJsonRouteZ(task, ctx)
  }

  /**
   * return a single user identified by email
   */
  private def getUserByEmail(): Route =
    path(usersBasePath / "email" / Segment)(email => get(getUser(UserIdentifierADM(maybeEmail = Some(email)), _)))

  /**
   * return a single user identified by username
   */
  private def getUserByUsername(): Route =
    path(usersBasePath / "username" / Segment)(username =>
      get(getUser(UserIdentifierADM(maybeUsername = Some(username)), _))
    )

  /**
   * Change existing user's basic information.
   */
  private def changeUserBasicInformation(): Route =
    path(usersBasePath / "iri" / Segment / "BasicUserInformation") { userIri =>
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

          val maybeUsername: Option[Username] = apiRequest.username match {
            case Some(username) => Username.make(username).fold(e => throw e.head, v => Some(v))
            case None           => None
          }

          val maybeEmail: Option[Email] = apiRequest.email match {
            case Some(email) => Email.make(email).fold(e => throw e.head, v => Some(v))
            case None        => None
          }

          val maybeGivenName: Option[GivenName] = apiRequest.givenName match {
            case Some(givenName) => GivenName.make(givenName).fold(e => throw e.head, v => Some(v))
            case None            => None
          }

          val maybeFamilyName: Option[FamilyName] = apiRequest.familyName match {
            case Some(familyName) => FamilyName.make(familyName).fold(e => throw e.head, v => Some(v))
            case None             => None
          }

          val maybeLanguageCode: Option[LanguageCode] = apiRequest.lang match {
            case Some(lang) => LanguageCode.make(lang).fold(e => throw e.head, v => Some(v))
            case None       => None
          }

          val userUpdatePayload: UserUpdateBasicInformationPayloadADM =
            UserUpdateBasicInformationPayloadADM(
              maybeUsername,
              maybeEmail,
              maybeGivenName,
              maybeFamilyName,
              maybeLanguageCode
            )

          val task = getUserUuid(requestContext)
            .map(r => UserChangeBasicInformationRequestADM(checkedUserIri, userUpdatePayload, r.user, r.uuid))
          RouteUtilADM.runJsonRouteZ(task, requestContext)
        }
      }
    }

  /**
   * Change user's password.
   */
  private def changeUserPassword(): Route =
    path(usersBasePath / "iri" / Segment / "Password") { userIri =>
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

          val updatePassword = UserUpdatePasswordPayloadADM(requesterPassword, changedPassword)
          val task = getUserUuid(requestContext).map(r =>
            UserChangePasswordRequestADM(checkedUserIri, updatePassword, r.user, r.uuid)
          )
          RouteUtilADM.runJsonRouteZ(task, requestContext)
        }
      }
    }

  /**
   * Change user's status.
   */
  private def changeUserStatus(): Route =
    path(usersBasePath / "iri" / Segment / "Status") { userIri =>
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

          val task =
            getUserUuid(requestContext).map(r => UserChangeStatusRequestADM(checkedUserIri, newStatus, r.user, r.uuid))
          runJsonRouteZ(task, requestContext)
        }
      }
    }

  /**
   * delete a user identified by iri (change status to false).
   */
  private def deleteUser(): Route = path(usersBasePath / "iri" / Segment) { userIri =>
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

      val task = getUserUuid(requestContext)
        .map(r => UserChangeStatusRequestADM(checkedUserIri, status, r.user, r.uuid))
      runJsonRouteZ(task, requestContext)
    }
  }

  /**
   * Change user's SystemAdmin membership.
   */
  private def changeUserSystemAdminMembership(): Route =
    path(usersBasePath / "iri" / Segment / "SystemAdmin") { userIri =>
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

          val task = getUserUuid(requestContext)
            .map(r => UserChangeSystemAdminMembershipStatusRequestADM(checkedUserIri, newSystemAdmin, r.user, r.uuid))
          runJsonRouteZ(task, requestContext)
        }
      }
    }

  /**
   * get user's project memberships
   */
  private def getUsersProjectMemberships(): Route =
    path(usersBasePath / "iri" / Segment / "project-memberships") { userIri =>
      get { requestContext =>
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        val checkedUserIri =
          stringFormatter.validateAndEscapeUserIri(userIri, throw BadRequestException(s"Invalid user IRI $userIri"))

        val requestTask =
          Authenticator.getUserADM(requestContext).map(UserProjectMembershipsGetRequestADM(checkedUserIri, _))

        runJsonRouteZ(requestTask, requestContext)
      }
    }

  /**
   * add user to project
   */
  private def addUserToProjectMembership(): Route =
    path(usersBasePath / "iri" / Segment / "project-memberships" / Segment) { (userIri, projectIri) =>
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

        val task = getIriUserUuid(projectIri, requestContext).map(r =>
          UserProjectMembershipAddRequestADM(checkedUserIri, r.iri, r.user, r.uuid)
        )
        runJsonRouteZ(task, requestContext)
      }
    }

  /**
   * remove user from project (and all groups belonging to this project)
   */
  private def removeUserFromProjectMembership(): Route =
    path(usersBasePath / "iri" / Segment / "project-memberships" / Segment) { (userIri, projectIri) =>
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

        val task = getIriUserUuid(projectIri, requestContext).map(r =>
          UserProjectMembershipRemoveRequestADM(checkedUserIri, r.iri, r.user, r.uuid)
        )
        runJsonRouteZ(task, requestContext)
      }
    }

  /**
   * get user's project admin memberships
   */
  private def getUsersProjectAdminMemberships(): Route =
    path(usersBasePath / "iri" / Segment / "project-admin-memberships") { userIri =>
      get { requestContext =>
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")
        val task = getIriUserUuid(userIri, requestContext).map(r =>
          UserProjectAdminMembershipsGetRequestADM(r.iri, r.user, r.uuid)
        )
        runJsonRouteZ(task, requestContext)
      }
    }

  /**
   * add user to project admin
   */
  private def addUserToProjectAdminMembership(): Route =
    path(usersBasePath / "iri" / Segment / "project-admin-memberships" / Segment) { (userIri, projectIri) =>
      post { ctx =>
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

        val task = getIriUserUuid(projectIri, ctx).map(r =>
          UserProjectAdminMembershipAddRequestADM(checkedUserIri, r.iri, r.user, r.uuid)
        )
        runJsonRouteZ(task, ctx)
      }
    }

  /**
   * remove user from project admin membership
   */
  private def removeUserFromProjectAdminMembership(): Route =
    path(usersBasePath / "iri" / Segment / "project-admin-memberships" / Segment) { (userIri, projectIri) =>
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

        val task = getIriUserUuid(projectIri, requestContext).map(r =>
          UserProjectAdminMembershipRemoveRequestADM(checkedUserIri, r.iri, r.user, r.uuid)
        )
        runJsonRouteZ(task, requestContext)
      }
    }

  /**
   * get user's group memberships
   */
  private def getUsersGroupMemberships(): Route =
    path(usersBasePath / "iri" / Segment / "group-memberships") { userIri =>
      get { ctx =>
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")
        val requestTask = getIriUser(userIri, ctx).map(r => UserGroupMembershipsGetRequestADM(r.iri, r.user))
        runJsonRouteZ(requestTask, ctx)
      }
    }

  /**
   * add user to group
   */
  private def addUserToGroupMembership(): Route =
    path(usersBasePath / "iri" / Segment / "group-memberships" / Segment) { (userIri, groupIri) =>
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
          StringFormatter
            .validateAndEscapeIri(groupIri)
            .getOrElse(throw BadRequestException(s"Invalid group IRI $groupIri"))

        val task = getIriUserUuid(groupIri, requestContext).map(r =>
          UserGroupMembershipAddRequestADM(checkedUserIri, checkedGroupIri, r.user, r.uuid)
        )
        runJsonRouteZ(task, requestContext)
      }
    }

  /**
   * remove user from group
   */
  private def removeUserFromGroupMembership(): Route =
    path(usersBasePath / "iri" / Segment / "group-memberships" / Segment) { (userIri, groupIri) =>
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
          StringFormatter
            .validateAndEscapeIri(groupIri)
            .getOrElse(throw BadRequestException(s"Invalid group IRI $groupIri"))

        val task = getIriUserUuid(groupIri, requestContext).map(r =>
          UserGroupMembershipRemoveRequestADM(checkedUserIri, checkedGroupIri, r.user, r.uuid)
        )
        runJsonRouteZ(task, requestContext)
      }
    }
}
