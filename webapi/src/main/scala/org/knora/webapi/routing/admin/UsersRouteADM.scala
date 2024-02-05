/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.PathMatcher
import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol.*
import org.knora.webapi.messages.admin.responder.usersmessages.*
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilADM
import org.knora.webapi.routing.RouteUtilADM.getIriUser
import org.knora.webapi.routing.RouteUtilADM.getIriUserUuid
import org.knora.webapi.routing.RouteUtilADM.getUserUuid
import org.knora.webapi.routing.RouteUtilADM.runJsonRouteZ
import org.knora.webapi.slice.admin.domain.model.*

/**
 * Provides an pekko-http-routing function for API routes that deal with users.
 */
final case class UsersRouteADM()(
  private implicit val runtime: Runtime[Authenticator & StringFormatter & MessageRelay]
) {

  private val usersBasePath: PathMatcher[Unit] = PathMatcher("admin" / "users")

  def makeRoute: Route =
    addUser() ~
      changeUserBasicInformation() ~
      changeUserPassword() ~
      changeUserStatus() ~
      changeUserSystemAdminMembership() ~
      getUsersProjectMemberships() ~
      addUserToProjectMembership() ~
      removeUserFromProjectMembership() ~
      getUsersProjectAdminMemberships ~
      addUserToProjectAdminMembership() ~
      removeUserFromProjectAdminMembership() ~
      getUsersGroupMemberships ~
      addUserToGroupMembership() ~
      removeUserFromGroupMembership()

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
   * Change existing user's basic information.
   */
  private def changeUserBasicInformation(): Route =
    path(usersBasePath / "iri" / Segment / "BasicUserInformation") { userIri =>
      put {
        entity(as[ChangeUserApiRequestADM]) { apiRequest => requestContext =>
          val task = for {
            checkedUserIri <- validateUserIriAndEnsureRegularUser(userIri)
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
            checkedUserIri <- validateUserIriAndEnsureRegularUser(userIri)
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
                           .fromOption(apiRequest.status.map(UserStatus.from))
                           .orElseFail(BadRequestException("The status is missing."))
            checkedUserIri <- validateUserIriAndEnsureRegularUser(userIri)
            r              <- getUserUuid(requestContext)
          } yield UserChangeStatusRequestADM(checkedUserIri, newStatus, r.user, r.uuid)
          runJsonRouteZ(task, requestContext)
        }
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
            checkedUserIri <- validateUserIriAndEnsureRegularUser(userIri)
            r              <- getUserUuid(requestContext)
            newSystemAdmin <- ZIO
                                .fromOption(apiRequest.systemAdmin.map(SystemAdmin.from))
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
          checkedUserIri <- validateUserIriAndEnsureRegularUser(userIri)
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
          userIri <- validateUserIriAndEnsureRegularUser(userIri)
          r       <- getIriUserUuid(projectIri, requestContext)
        } yield UserProjectMembershipAddRequestADM(userIri, r.iri, r.user, r.uuid)
        runJsonRouteZ(task, requestContext)
      }
    }

  private def validateUserIriAndEnsureRegularUser(userIri: String) =
    ZIO
      .fromEither(UserIri.from(userIri))
      .filterOrFail(_.isRegularUser)("Changes to built-in users are not allowed.")
      .mapBoth(BadRequestException.apply, _.value)

  private def validateAndEscapeGroupIri(groupIri: String) =
    Iri
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
          checkedUserIri <- validateUserIriAndEnsureRegularUser(userIri)
          r              <- getIriUserUuid(projectIri, requestContext)
        } yield UserProjectMembershipRemoveRequestADM(checkedUserIri, r.iri, r.user, r.uuid)
        runJsonRouteZ(task, requestContext)
      }
    }

  /**
   * get user's project admin memberships
   */
  private def getUsersProjectAdminMemberships: Route =
    path(usersBasePath / "iri" / Segment / "project-admin-memberships") { userIri =>
      get { requestContext =>
        val task = for {
          _ <- ZIO.fail(BadRequestException("User IRI cannot be empty")).when(userIri.isEmpty)
          r <- getIriUserUuid(userIri, requestContext)
        } yield UserProjectAdminMembershipsGetRequestADM(r.iri, r.user, r.uuid)
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
          checkedUserIri <- validateUserIriAndEnsureRegularUser(userIri)
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
          checkedUserIri <- validateUserIriAndEnsureRegularUser(userIri)
          r              <- getIriUserUuid(projectIri, requestContext)
        } yield UserProjectAdminMembershipRemoveRequestADM(checkedUserIri, r.iri, r.user, r.uuid)
        runJsonRouteZ(task, requestContext)
      }
    }

  /**
   * get user's group memberships
   */
  private def getUsersGroupMemberships: Route =
    path(usersBasePath / "iri" / Segment / "group-memberships") { userIri =>
      get { ctx =>
        val requestTask = for {
          _ <- ZIO.fail(BadRequestException("User IRI cannot be empty")).when(userIri.isEmpty)
          r <- getIriUser(userIri, ctx)
        } yield UserGroupMembershipsGetRequestADM(r.iri, r.user)
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
          checkedUserIri  <- validateUserIriAndEnsureRegularUser(userIri)
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
          checkedUserIri  <- validateUserIriAndEnsureRegularUser(userIri)
          checkedGroupIri <- validateAndEscapeGroupIri(groupIri)
          r               <- getIriUserUuid(groupIri, requestContext)
        } yield UserGroupMembershipRemoveRequestADM(checkedUserIri, checkedGroupIri, r.user, r.uuid)
        runJsonRouteZ(task, requestContext)
      }
    }
}
