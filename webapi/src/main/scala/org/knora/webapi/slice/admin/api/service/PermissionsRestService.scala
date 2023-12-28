/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.Random
import zio.Task
import zio.ZLayer

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateAdministrativePermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateDefaultObjectAccessPermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionDeleteResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.responders.admin.PermissionsResponderADM
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.common.api.AuthorizationRestService

final case class PermissionsRestService(
  responder: PermissionsResponderADM,
  projectRepo: KnoraProjectRepo,
  auth: AuthorizationRestService
) {
  def createAdministrativePermission(
    request: CreateAdministrativePermissionAPIRequestADM,
    user: UserADM
  ): Task[AdministrativePermissionCreateResponseADM] = for {
    _      <- ensureProjectIriStrExistsAndUserHasAccess(request.forProject, user)
    uuid   <- Random.nextUUID
    result <- responder.createAdministrativePermission(request, user, uuid)
  } yield result

  private def ensureProjectIriStrExistsAndUserHasAccess(projectIri: String, user: UserADM): Task[Unit] = for {
    projectIri <- KnoraProject.ProjectIri
                    .from(projectIri)
                    .toZIO
                    .mapError(e => BadRequestException(s"Invalid projectIri: ${e.getMessage}"))
    _ <- ensureProjectIriExistsAndUserHasAccess(projectIri, user)
  } yield ()

  private def ensureProjectIriExistsAndUserHasAccess(projectIri: ProjectIri, user: UserADM): Task[Unit] =
    projectRepo
      .findById(projectIri)
      .someOrFail(NotFoundException(s"Project ${projectIri.value} not found"))
      .flatMap(auth.ensureSystemAdminOrProjectAdmin(user, _))

  def getPermissionsApByProjectIri(
    value: ProjectIri,
    user: UserADM
  ): Task[AdministrativePermissionsForProjectGetResponseADM] = for {
    _      <- ensureProjectIriExistsAndUserHasAccess(value, user)
    result <- responder.getPermissionsApByProjectIri(value.value)
  } yield result

  def deletePermission(permissionIri: PermissionIri, user: UserADM): Task[PermissionDeleteResponseADM] = for {
    _      <- auth.ensureSystemAdmin(user)
    uuid   <- Random.nextUUID
    result <- responder.deletePermission(permissionIri, user, uuid)
  } yield result

  def createDefaultObjectAccessPermission(
    request: CreateDefaultObjectAccessPermissionAPIRequestADM,
    user: UserADM
  ): Task[DefaultObjectAccessPermissionCreateResponseADM] =
    for {
      _      <- ensureProjectIriStrExistsAndUserHasAccess(request.forProject, user)
      uuid   <- Random.nextUUID
      result <- responder.createDefaultObjectAccessPermission(request, user, uuid)
    } yield result

  def getPermissionsDaopByProjectIri(projectIri: ProjectIri, user: UserADM) =
    for {
      _      <- ensureProjectIriExistsAndUserHasAccess(projectIri, user)
      result <- responder.getPermissionsDaopByProjectIri(projectIri)
    } yield result

  def getPermissionsApByProjectAndGroupIri(
    projectIri: ProjectIri,
    groupIri: GroupIri,
    user: UserADM
  ): Task[AdministrativePermissionGetResponseADM] = for {
    _      <- ensureProjectIriExistsAndUserHasAccess(projectIri, user)
    result <- responder.getPermissionsApByProjectAndGroupIri(projectIri.value, groupIri.value)
  } yield result
}

object PermissionsRestService {

  val layer = ZLayer.derive[PermissionsRestService]
}