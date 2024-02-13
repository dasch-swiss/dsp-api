/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.messages.admin.responder.usersmessages.UserOperationResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.BasicUserInformationChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.UserCreateRequest
import org.knora.webapi.slice.admin.api.service.UsersRestService
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

case class UsersEndpointsHandler(
  usersEndpoints: UsersEndpoints,
  restService: UsersRestService,
  mapper: HandlerMapper
) {

  private val getUsersHandler = SecuredEndpointHandler[Unit, UsersGetResponseADM](
    usersEndpoints.getUsers,
    requestingUser => _ => restService.listAllUsers(requestingUser)
  )

  private val getUserByIriHandler = SecuredEndpointHandler[UserIri, UserResponseADM](
    usersEndpoints.getUserByIri,
    requestingUser => userIri => restService.getUserByIri(requestingUser, userIri)
  )

  private val getUserByEmailHandler = SecuredEndpointHandler[Email, UserResponseADM](
    usersEndpoints.getUserByEmail,
    requestingUser => email => restService.getUserByEmail(requestingUser, email)
  )

  private val getUserByUsernameHandler = SecuredEndpointHandler[Username, UserResponseADM](
    usersEndpoints.getUserByUsername,
    requestingUser => username => restService.getUserByUsername(requestingUser, username)
  )

  private val getUsersByIriProjectMemberShipsHandler = PublicEndpointHandler(
    usersEndpoints.getUsersByIriProjectMemberShips,
    restService.getProjectMemberShipsByIri
  )

  private val getUsersByIriProjectAdminMemberShipsHandler = PublicEndpointHandler(
    usersEndpoints.getUsersByIriProjectAdminMemberShips,
    restService.getProjectAdminMemberShipsByIri
  )

  private val getUsersByIriGroupMembershipsHandler = PublicEndpointHandler(
    usersEndpoints.getUsersByIriGroupMemberships,
    restService.getGroupMemberShipsByIri
  )

  // Create
  private val createUserHandler = SecuredEndpointHandler[UserCreateRequest, UserOperationResponseADM](
    usersEndpoints.postUsers,
    requestingUser => userCreateRequest => restService.createUser(requestingUser, userCreateRequest)
  )

  // Update
  private val putUsersIriBasicInformationHandler =
    SecuredEndpointHandler[(UserIri, BasicUserInformationChangeRequest), UserOperationResponseADM](
      usersEndpoints.putUsersIriBasicInformation,
      requestingUser => { case (userIri: UserIri, changeRequest: BasicUserInformationChangeRequest) =>
        restService.updateUser(requestingUser, userIri, changeRequest)
      }
    )

  // Deletes
  private val deleteUserByIriHandler = SecuredEndpointHandler[UserIri, UserOperationResponseADM](
    usersEndpoints.deleteUser,
    requestingUser => userIri => restService.deleteUser(requestingUser, userIri)
  )

  private val public = List(
    getUsersByIriProjectMemberShipsHandler,
    getUsersByIriProjectAdminMemberShipsHandler,
    getUsersByIriGroupMembershipsHandler
  ).map(mapper.mapPublicEndpointHandler(_))

  private val secured = List(
    getUsersHandler,
    getUserByIriHandler,
    getUserByEmailHandler,
    getUserByUsernameHandler,
    createUserHandler,
    putUsersIriBasicInformationHandler,
    deleteUserByIriHandler
  ).map(mapper.mapSecuredEndpointHandler(_))

  val allHanders = public ++ secured
}

object UsersEndpointsHandler {
  val layer = ZLayer.derive[UsersEndpointsHandler]
}
