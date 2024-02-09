/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.*

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.messages.admin.responder.usersmessages.UserGroupMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserOperationResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectAdminMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserProjectMembershipsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.responders.admin.UsersResponder
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

final case class UsersRestService(
                                   auth: AuthorizationRestService,
                                   responder: UsersResponder,
                                   format: KnoraResponseRenderer
) {

  def listAllUsers(user: User): Task[UsersGetResponseADM] = for {
    internal <- responder.getAllUserADMRequest(user)
    external <- format.toExternal(internal)
  } yield external

  def deleteUser(requestingUser: User, deleteIri: UserIri): Task[UserOperationResponseADM] = for {
    _ <- ZIO
           .fail(BadRequestException("Changes to built-in users are not allowed."))
           .when(deleteIri.isBuiltInUser)
    uuid     <- Random.nextUUID
    internal <- responder.changeUserStatusADM(deleteIri.value, UserStatus.Inactive, requestingUser, uuid)
    external <- format.toExternal(internal)
  } yield external

  def getUserByEmail(requestingUser: User, email: Email): Task[UserResponseADM] = for {
    internal <- responder
                  .findUserByEmail(email, UserInformationTypeADM.Restricted, requestingUser)
                  .someOrFail(NotFoundException(s"User with email '${email.value}' not found"))
                  .map(UserResponseADM.apply)
    external <- format.toExternal(internal)
  } yield external

  def getGroupMemberShipsByIri(userIri: UserIri): Task[UserGroupMembershipsGetResponseADM] =
    responder.findGroupMembershipsByIri(userIri).map(UserGroupMembershipsGetResponseADM).flatMap(format.toExternal)

  def createUser(requestingUser: User, userCreateRequest: Requests.UserCreateRequest): Task[UserOperationResponseADM] =
    for {
      _        <- auth.ensureSystemAdmin(requestingUser)
      uuid     <- Random.nextUUID
      internal <- responder.createNewUserADM(userCreateRequest, uuid)
      external <- format.toExternal(internal)
    } yield external

  def getProjectMemberShipsByIri(userIri: UserIri): Task[UserProjectMembershipsGetResponseADM] =
    responder.findProjectMemberShipsByIri(userIri).flatMap(format.toExternal)

  def getProjectAdminMemberShipsByIri(userIri: UserIri): Task[UserProjectAdminMembershipsGetResponseADM] =
    responder.findUserProjectAdminMemberships(userIri).flatMap(format.toExternal)

  def getUserByUsername(requestingUser: User, username: Username): Task[UserResponseADM] = for {
    internal <- responder
                  .findUserByUsername(username, UserInformationTypeADM.Restricted, requestingUser)
                  .someOrFail(NotFoundException(s"User with username '${username.value}' not found"))
                  .map(UserResponseADM.apply)
    external <- format.toExternal(internal)
  } yield external

  def getUserByIri(requestingUser: User, userIri: UserIri): Task[UserResponseADM] = for {
    internal <- responder
                  .findUserByIri(userIri, UserInformationTypeADM.Restricted, requestingUser)
                  .someOrFail(NotFoundException(s"User '${userIri.value}' not found"))
                  .map(UserResponseADM.apply)
    external <- format.toExternal(internal)
  } yield external
}

object UsersRestService {
  val layer = ZLayer.derive[UsersRestService]
}
