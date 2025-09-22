/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.BasicUserInformationChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.PasswordChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.StatusChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.SystemAdminChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.UserCreateRequest
import org.knora.webapi.slice.admin.api.service.UserRestService
import org.knora.webapi.slice.admin.api.service.UserRestService.UserResponse
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

  private val public = List(
    PublicEndpointHandler(usersEndpoints.get.usersByIriProjectMemberShips, restService.getProjectMemberShipsByUserIri),
    PublicEndpointHandler(
      usersEndpoints.get.usersByIriProjectAdminMemberShips,
      restService.getProjectAdminMemberShipsByUserIri,
    ),
    PublicEndpointHandler(usersEndpoints.get.usersByIriGroupMemberships, restService.getGroupMemberShipsByIri),
  ).map(mapper.mapPublicEndpointHandler(_))

  private val secured = List(
    SecuredEndpointHandler(usersEndpoints.get.users, user => _ => restService.getAllUsers(user)),
    SecuredEndpointHandler(usersEndpoints.get.userByIri, restService.getUserByIri),
    SecuredEndpointHandler(usersEndpoints.get.userByEmail, restService.getUserByEmail),
    SecuredEndpointHandler(usersEndpoints.get.userByUsername, restService.getUserByUsername),
    SecuredEndpointHandler(usersEndpoints.post.users, restService.createUser),
    SecuredEndpointHandler(usersEndpoints.post.usersByIriProjectMemberShips, restService.addUserToProject),
    SecuredEndpointHandler(usersEndpoints.post.usersByIriProjectAdminMemberShips, restService.addUserToProjectAsAdmin),
    SecuredEndpointHandler(usersEndpoints.post.usersByIriGroupMemberShips, restService.addUserToGroup),
    SecuredEndpointHandler(usersEndpoints.put.usersIriBasicInformation, restService.updateUser),
    SecuredEndpointHandler(usersEndpoints.put.usersIriPassword, restService.changePassword),
    SecuredEndpointHandler(usersEndpoints.put.usersIriStatus, restService.changeStatus),
    SecuredEndpointHandler(usersEndpoints.put.usersIriSystemAdmin, restService.changeSystemAdmin),
    SecuredEndpointHandler(usersEndpoints.delete.deleteUser, restService.deleteUser),
    SecuredEndpointHandler(usersEndpoints.delete.usersByIriProjectMemberShips, restService.removeUserFromProject),
    SecuredEndpointHandler(
      usersEndpoints.delete.usersByIriProjectAdminMemberShips,
      restService.removeUserFromProjectAsAdmin,
    ),
    SecuredEndpointHandler(usersEndpoints.delete.usersByIriGroupMemberShips, restService.removeUserFromGroup),
  ).map(mapper.mapSecuredEndpointHandler(_))

  val allHanders = public ++ secured
}

object UsersEndpointsHandler {
  val layer = ZLayer.derive[UsersEndpointsHandler]
}
