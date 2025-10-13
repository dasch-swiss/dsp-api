/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.ztapir.*
import zio.*

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

case class UsersServerEndpoints(
  private val usersEndpoints: UsersEndpoints,
  private val restService: UserRestService,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    usersEndpoints.get.usersByIriProjectMemberShips.zServerLogic(restService.getProjectMemberShipsByUserIri),
    usersEndpoints.get.usersByIriProjectAdminMemberShips.zServerLogic(restService.getProjectAdminMemberShipsByUserIri),
    usersEndpoints.get.usersByIriGroupMemberships.zServerLogic(restService.getGroupMemberShipsByIri),
    usersEndpoints.get.users.serverLogic(user => _ => restService.getAllUsers(user)),
    usersEndpoints.get.userByIri.serverLogic(restService.getUserByIri),
    usersEndpoints.get.userByEmail.serverLogic(restService.getUserByEmail),
    usersEndpoints.get.userByUsername.serverLogic(restService.getUserByUsername),
    usersEndpoints.post.users.serverLogic(restService.createUser),
    usersEndpoints.post.usersByIriProjectMemberShips.serverLogic(restService.addUserToProject),
    usersEndpoints.post.usersByIriProjectAdminMemberShips.serverLogic(restService.addUserToProjectAsAdmin),
    usersEndpoints.post.usersByIriGroupMemberShips.serverLogic(restService.addUserToGroup),
    usersEndpoints.put.usersIriBasicInformation.serverLogic(restService.updateUser),
    usersEndpoints.put.usersIriPassword.serverLogic(restService.changePassword),
    usersEndpoints.put.usersIriStatus.serverLogic(restService.changeStatus),
    usersEndpoints.put.usersIriSystemAdmin.serverLogic(restService.changeSystemAdmin),
    usersEndpoints.delete.deleteUser.serverLogic(restService.deleteUser),
    usersEndpoints.delete.usersByIriProjectMemberShips.serverLogic(restService.removeUserFromProject),
    usersEndpoints.delete.usersByIriProjectAdminMemberShips.serverLogic(restService.removeUserFromProjectAsAdmin),
    usersEndpoints.delete.usersByIriGroupMemberShips.serverLogic(restService.removeUserFromGroup),
  )
}

object UsersServerEndpoints {
  val layer = ZLayer.derive[UsersServerEndpoints]
}
