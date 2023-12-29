/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.NonEmptyChunk
import zio.Random
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionGroupApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionHasPermissionsApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionPropertyApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionResourceClassApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateAdministrativePermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateDefaultObjectAccessPermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionDeleteResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.responders.admin.PermissionsResponderADM
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

final case class PermissionsRestService(
  responder: PermissionsResponderADM,
  projectRepo: KnoraProjectRepo,
  auth: AuthorizationRestService,
  format: KnoraResponseRenderer
) {
  def createAdministrativePermission(
    request: CreateAdministrativePermissionAPIRequestADM,
    user: UserADM
  ): Task[AdministrativePermissionCreateResponseADM] =
    for {
      _      <- ensureProjectIriStrExistsAndUserHasAccess(request.forProject, user)
      uuid   <- Random.nextUUID
      result <- responder.createAdministrativePermission(request, user, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  private def ensureProjectIriStrExistsAndUserHasAccess(projectIri: String, user: UserADM): Task[KnoraProject] =
    for {
      projectIri <- KnoraProject.ProjectIri
                      .from(projectIri)
                      .toZIO
                      .mapError(e => BadRequestException(s"Invalid projectIri: ${e.getMessage}"))
      project <- ensureProjectIriExistsAndUserHasAccess(projectIri, user)
    } yield project

  private def ensureProjectIriExistsAndUserHasAccess(projectIri: ProjectIri, user: UserADM): Task[KnoraProject] =
    projectRepo
      .findById(projectIri)
      .someOrFail(NotFoundException(s"Project ${projectIri.value} not found"))
      .tap(auth.ensureSystemAdminOrProjectAdmin(user, _))

  def getPermissionsApByProjectIri(
    value: ProjectIri,
    user: UserADM
  ): Task[AdministrativePermissionsForProjectGetResponseADM] =
    for {
      _      <- ensureProjectIriExistsAndUserHasAccess(value, user)
      result <- responder.getPermissionsApByProjectIri(value.value)
      ext    <- format.toExternal(result)
    } yield ext

  def getPermissionsByProjectIri(projectIri: ProjectIri, user: UserADM): Task[PermissionsForProjectGetResponseADM] =
    for {
      _      <- ensureProjectIriExistsAndUserHasAccess(projectIri, user)
      result <- responder.getPermissionsByProjectIri(projectIri)
      ext    <- format.toExternal(result)
    } yield ext

  def deletePermission(permissionIri: PermissionIri, user: UserADM): Task[PermissionDeleteResponseADM] =
    for {
      _      <- auth.ensureSystemAdmin(user)
      uuid   <- Random.nextUUID
      result <- responder.deletePermission(permissionIri, user, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def createDefaultObjectAccessPermission(
    request: CreateDefaultObjectAccessPermissionAPIRequestADM,
    user: UserADM
  ): Task[DefaultObjectAccessPermissionCreateResponseADM] =
    for {
      _      <- ensureProjectIriStrExistsAndUserHasAccess(request.forProject, user)
      uuid   <- Random.nextUUID
      result <- responder.createDefaultObjectAccessPermission(request, user, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def updatePermissionHasPermissions(
    permissionIri: PermissionIri,
    request: ChangePermissionHasPermissionsApiRequestADM,
    user: UserADM
  ): Task[PermissionGetResponseADM] =
    for {
      _    <- auth.ensureSystemAdmin(user)
      uuid <- Random.nextUUID
      newHasPermissions <- ZIO
                             .fromOption(NonEmptyChunk.fromIterableOption(request.hasPermissions))
                             .mapBoth(_ => BadRequestException("hasPermissions must not be empty"), identity)
      result <- responder.updatePermissionHasPermissions(permissionIri, newHasPermissions, user, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def updatePermissionProperty(
    permissionIri: PermissionIri,
    request: ChangePermissionPropertyApiRequestADM,
    user: UserADM
  ): Task[PermissionGetResponseADM] =
    for {
      _      <- auth.ensureSystemAdmin(user)
      uuid   <- Random.nextUUID
      result <- responder.updatePermissionProperty(permissionIri, request, user, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def updatePermissionResourceClass(
    permissionIri: PermissionIri,
    request: ChangePermissionResourceClassApiRequestADM,
    user: UserADM
  ): Task[PermissionGetResponseADM] =
    for {
      _      <- auth.ensureSystemAdmin(user)
      uuid   <- Random.nextUUID
      result <- responder.updatePermissionResourceClass(permissionIri, request, user, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def updatePermissionGroup(
    permissionIri: PermissionIri,
    request: ChangePermissionGroupApiRequestADM,
    user: UserADM
  ): Task[PermissionGetResponseADM] =
    for {
      _        <- auth.ensureSystemAdmin(user)
      groupIri <- ZIO.fromEither(GroupIri.from(request.forGroup)).mapError(BadRequestException(_))
      uuid     <- Random.nextUUID
      result   <- responder.updatePermissionsGroup(permissionIri, groupIri, user, uuid)
      ext      <- format.toExternal(result)
    } yield ext

  def getPermissionsDaopByProjectIri(
    projectIri: ProjectIri,
    user: UserADM
  ): Task[DefaultObjectAccessPermissionsForProjectGetResponseADM] =
    for {
      _      <- ensureProjectIriExistsAndUserHasAccess(projectIri, user)
      result <- responder.getPermissionsDaopByProjectIri(projectIri)
      ext    <- format.toExternal(result)
    } yield ext

  def getPermissionsApByProjectAndGroupIri(
    projectIri: ProjectIri,
    groupIri: GroupIri,
    user: UserADM
  ): Task[AdministrativePermissionGetResponseADM] =
    for {
      _      <- ensureProjectIriExistsAndUserHasAccess(projectIri, user)
      result <- responder.getPermissionsApByProjectAndGroupIri(projectIri.value, groupIri.value)
      ext    <- format.toExternal(result)
    } yield ext
}

object PermissionsRestService {

  val layer = ZLayer.derive[PermissionsRestService]
}
