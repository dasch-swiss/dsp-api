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

import dsp.errors.BadRequestException
import dsp.valueobjects.User._
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.AnonymousUser
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.SystemUser
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilADM
import org.knora.webapi.routing.RouteUtilADM.getIriUser
import org.knora.webapi.routing.RouteUtilADM.getIriUserUuid
import org.knora.webapi.routing.RouteUtilADM.getUserUuid
import org.knora.webapi.routing.RouteUtilADM.runJsonRouteZ

/**
 * Provides an akka-http-routing function for API routes that deal with users.
 */
final case class UsersRouteADM()(
  private implicit val runtime: Runtime[Authenticator with StringFormatter with MessageRelay]
) {

  private val usersBasePath: PathMatcher[Unit] = PathMatcher("admin" / "users")

  def makeRoute: Route =
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
          payload <- UserCreatePayloadADM.make(apiRequest).mapError(BadRequestException(_)).toZIO
          r       <- getUserUuid(ctx)
        } yield UserCreateRequestADM(payload, r.user, r.uuid)
        runJsonRouteZ(requestTask, ctx)
      }
    }
  }

  /**
   * return a single user identified by iri
   */
  private def getUserByIri(): Route =
    path(usersBasePath / "iri" / Segment)(userIri => get(getUser(makeUserIdFromIri(userIri), _)))

  private def makeUserIdFromIri(iri: String) = ZIO.serviceWithZIO[StringFormatter] { implicit sf =>
    ZIO.attempt(UserIdentifierADM(maybeIri = Some(iri)))
  }

  private def makeUserIdFromEmail(email: String) = ZIO.serviceWithZIO[StringFormatter] { implicit sf =>
    ZIO.attempt(UserIdentifierADM(maybeEmail = Some(email)))
  }

  private def makeUserIdFromUsername(username: String) = ZIO.serviceWithZIO[StringFormatter] { implicit sf =>
    ZIO.attempt(UserIdentifierADM(maybeUsername = Some(username)))
  }

  private def getUser(id: ZIO[StringFormatter, Throwable, UserIdentifierADM], ctx: RequestContext) = {
    val task = for {
      userId         <- id
      requestingUser <- Authenticator.getUserADM(ctx)
    } yield UserGetRequestADM(userId, UserInformationTypeADM.Restricted, requestingUser)
    runJsonRouteZ(task, ctx)
  }

  /**
   * return a single user identified by email
   */
  private def getUserByEmail(): Route =
    path(usersBasePath / "email" / Segment)(email => get(getUser(makeUserIdFromEmail(email), _)))

  /**
   * return a single user identified by username
   */
  private def getUserByUsername(): Route =
    path(usersBasePath / "username" / Segment)(username => get(getUser(makeUserIdFromUsername(username), _)))

  /**
   * Change existing user's basic information.
   */
  private def changeUserBasicInformation(): Route =
    path(usersBasePath / "iri" / Segment / "BasicUserInformation") { userIri =>
      put {
        entity(as[ChangeUserApiRequestADM]) { apiRequest => requestContext =>
          val task = for {
            checkedUserIri <- validateAndEscapeUserIri(userIri)
            r              <- getUserUuid(requestContext)
            payload <- UserUpdateBasicInformationPayloadADM
                         .make(apiRequest)
                         .mapError(e => BadRequestException(e.getMessage))
                         .toZIO
                         .filterOrFail(_.isAtLeastOneParamSet)(BadRequestException("No data sent in API request."))
          } yield UserChangeBasicInformationRequestADM(checkedUserIri, payload, r.user, r.uuid)
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
          val task = for {
            checkedUserIri <- validateAndEscapeUserIri(userIri)
            payload        <- UserUpdatePasswordPayloadADM.make(apiRequest).mapError(BadRequestException(_)).toZIO
            r              <- getUserUuid(requestContext)
          } yield UserChangePasswordRequestADM(checkedUserIri, payload, r.user, r.uuid)
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
          val task = for {
            newStatus <- ZIO
                           .fromOption(apiRequest.status.map(UserStatus.make))
                           .orElseFail(BadRequestException("The status is missing."))
            checkedUserIri <- validateAndEscapeUserIri(userIri)
            r              <- getUserUuid(requestContext)
          } yield UserChangeStatusRequestADM(checkedUserIri, newStatus, r.user, r.uuid)
          runJsonRouteZ(task, requestContext)
        }
      }
    }

  /**
   * delete a user identified by iri (change status to false).
   */
  private def deleteUser(): Route = path(usersBasePath / "iri" / Segment) { userIri =>
    delete { requestContext =>
      val task = for {
        checkedUserIri <- validateAndEscapeUserIri(userIri)
        r              <- getUserUuid(requestContext)
      } yield UserChangeStatusRequestADM(checkedUserIri, UserStatus.make(false), r.user, r.uuid)
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
          val task = for {
            checkedUserIri <- validateAndEscapeUserIri(userIri)
            r              <- getUserUuid(requestContext)
            newSystemAdmin <- ZIO
                                .fromOption(apiRequest.systemAdmin.map(SystemAdmin.make))
                                .orElseFail(BadRequestException("The systemAdmin is missing."))
          } yield UserChangeSystemAdminMembershipStatusRequestADM(checkedUserIri, newSystemAdmin, r.user, r.uuid)
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
        val requestTask = for {
          checkedUserIri <- validateAndEscapeUserIri(userIri)
          requestingUser <- Authenticator.getUserADM(requestContext)
        } yield UserProjectMembershipsGetRequestADM(checkedUserIri, requestingUser)
        runJsonRouteZ(requestTask, requestContext)
      }
    }

  /**
   * add user to project
   */
  private def addUserToProjectMembership(): Route =
    path(usersBasePath / "iri" / Segment / "project-memberships" / Segment) { (userIri, projectIri) =>
      post { requestContext =>
        val task = for {
          checkedUserIri <- validateAndEscapeUserIri(userIri)
          r              <- getIriUserUuid(projectIri, requestContext)
        } yield UserProjectMembershipAddRequestADM(checkedUserIri, r.iri, r.user, r.uuid)
        runJsonRouteZ(task, requestContext)
      }
    }

  private def validateAndEscapeUserIri(userIri: String): ZIO[StringFormatter, BadRequestException, String] =
    for {
      nonEmptyIri <- ZIO.succeed(userIri).filterOrFail(_.nonEmpty)(BadRequestException("User IRI cannot be empty"))
      checkedUserIri <-
        ZIO.serviceWithZIO[StringFormatter] { sf =>
          ZIO
            .fromOption(sf.validateAndEscapeUserIri(nonEmptyIri))
            .orElseFail(BadRequestException(s"Invalid user IRI $userIri"))
            .filterOrFail(isNotBuildInUser)(BadRequestException("Changes to built-in users are not allowed."))
        }
    } yield checkedUserIri

  private def isNotBuildInUser(it: String) = !it.equals(SystemUser.id) && !it.equals(AnonymousUser.id)

  private def validateAndEscapeGroupIri(groupIri: String) =
    StringFormatter
      .validateAndEscapeIri(groupIri)
      .toZIO
      .orElseFail(BadRequestException(s"Invalid group IRI $groupIri"))

  /**
   * remove user from project (and all groups belonging to this project)
   */
  private def removeUserFromProjectMembership(): Route =
    path(usersBasePath / "iri" / Segment / "project-memberships" / Segment) { (userIri, projectIri) =>
      delete { requestContext =>
        val task = for {
          checkedUserIri <- validateAndEscapeUserIri(userIri)
          r              <- getIriUserUuid(projectIri, requestContext)
        } yield UserProjectMembershipRemoveRequestADM(checkedUserIri, r.iri, r.user, r.uuid)
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
        val task = for {
          checkedUserIri <- validateAndEscapeUserIri(userIri)
          r              <- getIriUserUuid(projectIri, ctx)
        } yield UserProjectAdminMembershipAddRequestADM(checkedUserIri, r.iri, r.user, r.uuid)
        runJsonRouteZ(task, ctx)
      }
    }

  /**
   * remove user from project admin membership
   */
  private def removeUserFromProjectAdminMembership(): Route =
    path(usersBasePath / "iri" / Segment / "project-admin-memberships" / Segment) { (userIri, projectIri) =>
      delete { requestContext =>
        val task = for {
          checkedUserIri <- validateAndEscapeUserIri(userIri)
          r              <- getIriUserUuid(projectIri, requestContext)
        } yield UserProjectAdminMembershipRemoveRequestADM(checkedUserIri, r.iri, r.user, r.uuid)
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
        val task = for {
          checkedUserIri  <- validateAndEscapeUserIri(userIri)
          checkedGroupIri <- validateAndEscapeGroupIri(groupIri)
          r               <- getIriUserUuid(groupIri, requestContext)
        } yield UserGroupMembershipAddRequestADM(checkedUserIri, checkedGroupIri, r.user, r.uuid)
        runJsonRouteZ(task, requestContext)
      }
    }

  /**
   * remove user from group
   */
  private def removeUserFromGroupMembership(): Route =
    path(usersBasePath / "iri" / Segment / "group-memberships" / Segment) { (userIri, groupIri) =>
      delete { requestContext =>
        val task = for {
          checkedUserIri  <- validateAndEscapeUserIri(userIri)
          checkedGroupIri <- validateAndEscapeGroupIri(groupIri)
          r               <- getIriUserUuid(groupIri, requestContext)
        } yield UserGroupMembershipRemoveRequestADM(checkedUserIri, checkedGroupIri, r.user, r.uuid)
        runJsonRouteZ(task, requestContext)
      }
    }
}
