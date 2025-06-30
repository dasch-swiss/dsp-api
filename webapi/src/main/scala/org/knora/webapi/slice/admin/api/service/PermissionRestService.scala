/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionADM
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
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionDeleteResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsForProjectGetResponseADM
import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.slice.admin.api.PermissionEndpointsRequests.ChangeDoapRequest
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.AdministrativePermissionService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

final case class PermissionRestService(
  responder: PermissionsResponder,
  knoraProjectService: KnoraProjectService,
  auth: AuthorizationRestService,
  format: KnoraResponseRenderer,
  administrativePermissionService: AdministrativePermissionService,
) {
  def createAdministrativePermission(
    request: CreateAdministrativePermissionAPIRequestADM,
    user: User,
  ): Task[AdministrativePermissionCreateResponseADM] =
    for {
      _      <- ensureProjectIriStrExistsAndUserHasAccess(request.forProject, user)
      uuid   <- Random.nextUUID
      result <- responder.createAdministrativePermission(request, user, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  private def ensureProjectIriStrExistsAndUserHasAccess(projectIri: String, user: User): Task[KnoraProject] =
    for {
      projectIri <- ZIO.fromEither(ProjectIri.from(projectIri)).mapError(BadRequestException.apply)
      project    <- ensureProjectIriExistsAndUserHasAccess(projectIri, user)
    } yield project

  private def ensureProjectIriExistsAndUserHasAccess(projectIri: ProjectIri, user: User): Task[KnoraProject] =
    knoraProjectService
      .findById(projectIri)
      .someOrFail(NotFoundException(s"Project ${projectIri.value} not found"))
      .tap(auth.ensureSystemAdminOrProjectAdmin(user, _))

  def getPermissionsApByProjectIri(
    value: ProjectIri,
    user: User,
  ): Task[AdministrativePermissionsForProjectGetResponseADM] =
    for {
      _      <- ensureProjectIriExistsAndUserHasAccess(value, user)
      result <- responder.getPermissionsApByProjectIri(value.value)
      ext    <- format.toExternal(result)
    } yield ext

  def getPermissionsByProjectIri(projectIri: ProjectIri, user: User): Task[PermissionsForProjectGetResponseADM] =
    for {
      _      <- ensureProjectIriExistsAndUserHasAccess(projectIri, user)
      result <- responder.getPermissionsByProjectIri(projectIri)
      ext    <- format.toExternal(result)
    } yield ext

  def deletePermission(permissionIri: PermissionIri, user: User): Task[PermissionDeleteResponseADM] =
    for {
      _      <- auth.ensureSystemAdmin(user)
      uuid   <- Random.nextUUID
      result <- responder.deletePermission(permissionIri, user, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def createDefaultObjectAccessPermission(
    request: CreateDefaultObjectAccessPermissionAPIRequestADM,
    user: User,
  ): Task[DefaultObjectAccessPermissionCreateResponseADM] =
    for {
      _      <- ensureProjectIriStrExistsAndUserHasAccess(request.forProject, user)
      uuid   <- Random.nextUUID
      result <- responder.createDefaultObjectAccessPermission(request, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def updatePermissionHasPermissions(
    permissionIri: PermissionIri,
    request: ChangePermissionHasPermissionsApiRequestADM,
    user: User,
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
    user: User,
  ): Task[DefaultObjectAccessPermissionGetResponseADM] =
    for {
      _      <- auth.ensureSystemAdmin(user)
      uuid   <- Random.nextUUID
      result <- responder.updatePermissionProperty(permissionIri, request, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def updatePermissionResourceClass(
    permissionIri: PermissionIri,
    request: ChangePermissionResourceClassApiRequestADM,
    user: User,
  ): Task[DefaultObjectAccessPermissionGetResponseADM] =
    for {
      _      <- auth.ensureSystemAdmin(user)
      uuid   <- Random.nextUUID
      result <- responder.updatePermissionResourceClass(permissionIri, request, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def updateDoapForWhat(
    iri: PermissionIri,
    req: ChangeDoapRequest,
    user: User,
  ): Task[DefaultObjectAccessPermissionGetResponseADM] =
    for {
      _      <- auth.ensureSystemAdmin(user)
      _      <- ZIO.fail(BadRequestException("No update provided.")).when(req.isEmpty)
      uuid   <- Random.nextUUID
      result <- responder.updateDoap(iri, req, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def updatePermissionGroup(
    permissionIri: PermissionIri,
    request: ChangePermissionGroupApiRequestADM,
    user: User,
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
    user: User,
  ): Task[DefaultObjectAccessPermissionsForProjectGetResponseADM] =
    for {
      _      <- ensureProjectIriExistsAndUserHasAccess(projectIri, user)
      result <- responder.getPermissionsDaopByProjectIri(projectIri)
      ext    <- format.toExternal(result)
    } yield ext

  def getPermissionsApByProjectAndGroupIri(
    projectIri: ProjectIri,
    groupIri: GroupIri,
    user: User,
  ): Task[AdministrativePermissionGetResponseADM] =
    for {
      _ <- ensureProjectIriExistsAndUserHasAccess(projectIri, user)
      result <-
        administrativePermissionService
          .findByGroupAndProject(groupIri, projectIri)
          .map(_.map(AdministrativePermissionADM.from))
          .someOrFail(
            NotFoundException(
              s"No Administrative Permission found for project: ${projectIri.value}, group: ${groupIri.value} combination",
            ),
          )
          .map(AdministrativePermissionGetResponseADM.apply)
      ext <- format.toExternal(result)
    } yield ext
}

object PermissionRestService {
  val layer = ZLayer.derive[PermissionRestService]
}
