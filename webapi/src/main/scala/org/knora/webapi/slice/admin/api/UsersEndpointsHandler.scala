/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.BasicUserInformationChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.PasswordChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.StatusChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.SystemAdminChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.UserCreateRequest
import org.knora.webapi.slice.admin.api.service.UserRestService
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

case class UsersEndpointsHandler(
  usersEndpoints: UsersEndpoints,
  restService: UserRestService,
  mapper: HandlerMapper,
) {

  private val getUsersHandler = SecuredEndpointHandler[Unit, UsersGetResponseADM](
    usersEndpoints.get.users,
    requestingUser => _ => restService.getAllUsers(requestingUser),
  )

  private val getUserByIriHandler = SecuredEndpointHandler[UserIri, UserResponseADM](
    usersEndpoints.get.userByIri,
    requestingUser => userIri => restService.getUserByIri(requestingUser, userIri),
  )

  private val getUserByEmailHandler = SecuredEndpointHandler[Email, UserResponseADM](
    usersEndpoints.get.userByEmail,
    requestingUser => email => restService.getUserByEmail(requestingUser, email),
  )

  private val getUserByUsernameHandler = SecuredEndpointHandler[Username, UserResponseADM](
    usersEndpoints.get.userByUsername,
    requestingUser => username => restService.getUserByUsername(requestingUser, username),
  )

  private val getUsersByIriProjectMemberShipsHandler = PublicEndpointHandler(
    usersEndpoints.get.usersByIriProjectMemberShips,
    restService.getProjectMemberShipsByUserIri,
  )

  private val getUsersByIriProjectAdminMemberShipsHandler = PublicEndpointHandler(
    usersEndpoints.get.usersByIriProjectAdminMemberShips,
    restService.getProjectAdminMemberShipsByUserIri,
  )

  private val getUsersByIriGroupMembershipsHandler = PublicEndpointHandler(
    usersEndpoints.get.usersByIriGroupMemberships,
    restService.getGroupMemberShipsByIri,
  )

  // Create
  private val createUserHandler = SecuredEndpointHandler[UserCreateRequest, UserResponseADM](
    usersEndpoints.post.users,
    requestingUser => userCreateRequest => restService.createUser(requestingUser, userCreateRequest),
  )

  private val postUsersByIriProjectMemberShipsHandler =
    SecuredEndpointHandler[(UserIri, ProjectIri), UserResponseADM](
      usersEndpoints.post.usersByIriProjectMemberShips,
      requestingUser => { case (userIri: UserIri, projectIri: ProjectIri) =>
        restService.addUserToProject(requestingUser, userIri, projectIri)
      },
    )

  private val postUsersByIriProjectAdminMemberShipsHandler =
    SecuredEndpointHandler[(UserIri, ProjectIri), UserResponseADM](
      usersEndpoints.post.usersByIriProjectAdminMemberShips,
      requestingUser => { case (userIri: UserIri, projectIri: ProjectIri) =>
        restService.addUserToProjectAsAdmin(requestingUser, userIri, projectIri)
      },
    )

  private val postUsersByIriGroupMemberShipsHandler =
    SecuredEndpointHandler[(UserIri, GroupIri), UserResponseADM](
      usersEndpoints.post.usersByIriGroupMemberShips,
      requestingUser => { case (userIri: UserIri, groupIri: GroupIri) =>
        restService.addUserToGroup(requestingUser, userIri, groupIri)
      },
    )

  // Update
  private val putUsersIriBasicInformationHandler =
    SecuredEndpointHandler[(UserIri, BasicUserInformationChangeRequest), UserResponseADM](
      usersEndpoints.put.usersIriBasicInformation,
      requestingUser => { case (userIri: UserIri, changeRequest: BasicUserInformationChangeRequest) =>
        restService.updateUser(requestingUser, userIri, changeRequest)
      },
    )

  private val putUsersIriPasswordHandler =
    SecuredEndpointHandler[(UserIri, PasswordChangeRequest), UserResponseADM](
      usersEndpoints.put.usersIriPassword,
      requestingUser => { case (userIri: UserIri, changeRequest: PasswordChangeRequest) =>
        restService.changePassword(requestingUser, userIri, changeRequest)
      },
    )

  private val putUsersIriStatusHandler =
    SecuredEndpointHandler[(UserIri, StatusChangeRequest), UserResponseADM](
      usersEndpoints.put.usersIriStatus,
      requestingUser => { case (userIri: UserIri, changeRequest: StatusChangeRequest) =>
        restService.changeStatus(requestingUser, userIri, changeRequest)
      },
    )

  private val putUsersIriSystemAdminHandler =
    SecuredEndpointHandler[(UserIri, SystemAdminChangeRequest), UserResponseADM](
      usersEndpoints.put.usersIriSystemAdmin,
      requestingUser => { case (userIri: UserIri, changeRequest: SystemAdminChangeRequest) =>
        restService.changeSystemAdmin(requestingUser, userIri, changeRequest)
      },
    )

  // Deletes
  private val deleteUserByIriHandler = SecuredEndpointHandler[UserIri, UserResponseADM](
    usersEndpoints.delete.deleteUser,
    requestingUser => userIri => restService.deleteUser(requestingUser, userIri),
  )

  private val deleteUsersByIriProjectMemberShipsHandler =
    SecuredEndpointHandler[(UserIri, ProjectIri), UserResponseADM](
      usersEndpoints.delete.usersByIriProjectMemberShips,
      requestingUser => { case (userIri: UserIri, projectIri: ProjectIri) =>
        restService.removeUserFromProject(requestingUser, userIri, projectIri)
      },
    )

  private val deleteUsersByIriProjectAdminMemberShipsHandler =
    SecuredEndpointHandler[(UserIri, ProjectIri), UserResponseADM](
      usersEndpoints.delete.usersByIriProjectAdminMemberShips,
      requestingUser => { case (userIri: UserIri, projectIri: ProjectIri) =>
        restService.removeUserFromProjectAsAdmin(requestingUser, userIri, projectIri)
      },
    )

  private val deleteUsersByIriGroupMemberShipsHandler =
    SecuredEndpointHandler[(UserIri, GroupIri), UserResponseADM](
      usersEndpoints.delete.usersByIriGroupMemberShips,
      requestingUser => { case (userIri: UserIri, groupIri: GroupIri) =>
        restService.removeUserFromGroup(requestingUser, userIri, groupIri)
      },
    )

  private val public = List(
    getUsersByIriProjectMemberShipsHandler,
    getUsersByIriProjectAdminMemberShipsHandler,
    getUsersByIriGroupMembershipsHandler,
  ).map(mapper.mapPublicEndpointHandler(_))

  private val secured = List(
    getUsersHandler,
    getUserByIriHandler,
    getUserByEmailHandler,
    getUserByUsernameHandler,
    createUserHandler,
    postUsersByIriProjectMemberShipsHandler,
    postUsersByIriProjectAdminMemberShipsHandler,
    postUsersByIriGroupMemberShipsHandler,
    putUsersIriBasicInformationHandler,
    putUsersIriPasswordHandler,
    putUsersIriStatusHandler,
    putUsersIriSystemAdminHandler,
    deleteUserByIriHandler,
    deleteUsersByIriProjectMemberShipsHandler,
    deleteUsersByIriProjectAdminMemberShipsHandler,
    deleteUsersByIriGroupMemberShipsHandler,
  ).map(mapper.mapSecuredEndpointHandler(_))

  val allHanders = public ++ secured
}

object UsersEndpointsHandler {
  val layer = ZLayer.derive[UsersEndpointsHandler]
}
