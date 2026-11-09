/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

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
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.service.AdministrativePermissionService
import org.knora.webapi.slice.api.admin.PermissionEndpointsRequests.ChangeDoapRequest
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.ProjectCandidate

final class PermissionRestService(
  responder: PermissionsResponder,
  auth: AuthorizationRestService,
  format: KnoraResponseRenderer,
  administrativePermissionService: AdministrativePermissionService,
) {

  def createAdministrativePermission(
    candidate: ProjectCandidate,
  )(request: CreateAdministrativePermissionAPIRequestADM): Task[AdministrativePermissionCreateResponseADM] =
    for {
      _      <- auth.authorizeProjectAdminById(candidate, request.forProject)
      uuid   <- Random.nextUUID
      result <- responder.createAdministrativePermission(request, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def getPermissionsApByProjectIri(
    candidate: ProjectCandidate,
  )(value: ProjectIri): Task[AdministrativePermissionsForProjectGetResponseADM] =
    for {
      grant  <- auth.authorizeProjectAdminById(candidate, value)
      result <- responder.getPermissionsApByProjectIri(grant.project.id)
      ext    <- format.toExternal(result)
    } yield ext

  def getPermissionsByProjectIri(
    candidate: ProjectCandidate,
  )(projectIri: ProjectIri): Task[PermissionsForProjectGetResponseADM] =
    for {
      grant  <- auth.authorizeProjectAdminById(candidate, projectIri)
      result <- responder.getPermissionsByProjectIri(grant.project.id)
      ext    <- format.toExternal(result)
    } yield ext

  def deletePermission(permissionIri: PermissionIri): Task[PermissionDeleteResponseADM] =
    for {
      uuid   <- Random.nextUUID
      result <- responder.deletePermission(permissionIri, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def createDefaultObjectAccessPermission(candidate: ProjectCandidate)(
    request: CreateDefaultObjectAccessPermissionAPIRequestADM,
  ): Task[DefaultObjectAccessPermissionCreateResponseADM] =
    for {
      _      <- auth.authorizeProjectAdminById(candidate, request.forProject)
      uuid   <- Random.nextUUID
      result <- responder.createDefaultObjectAccessPermission(request, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def updatePermissionHasPermissions(
    permissionIri: PermissionIri,
    request: ChangePermissionHasPermissionsApiRequestADM,
  ): Task[PermissionGetResponseADM] =
    for {
      uuid              <- Random.nextUUID
      newHasPermissions <- ZIO
                             .fromOption(NonEmptyChunk.fromIterableOption(request.hasPermissions))
                             .mapBoth(_ => BadRequestException("hasPermissions must not be empty"), identity)
      result <- responder.updatePermissionHasPermissions(permissionIri, newHasPermissions, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def updatePermissionProperty(
    permissionIri: PermissionIri,
    request: ChangePermissionPropertyApiRequestADM,
  ): Task[DefaultObjectAccessPermissionGetResponseADM] =
    for {
      uuid   <- Random.nextUUID
      result <- responder.updatePermissionProperty(permissionIri, request, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def updatePermissionResourceClass(
    permissionIri: PermissionIri,
    request: ChangePermissionResourceClassApiRequestADM,
  ): Task[DefaultObjectAccessPermissionGetResponseADM] =
    for {
      uuid   <- Random.nextUUID
      result <- responder.updatePermissionResourceClass(permissionIri, request, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def updateDoapForWhat(
    iri: PermissionIri,
    req: ChangeDoapRequest,
  ): Task[DefaultObjectAccessPermissionGetResponseADM] =
    for {
      _      <- ZIO.fail(BadRequestException("No update provided.")).when(req.isEmpty)
      uuid   <- Random.nextUUID
      result <- responder.updateDoap(iri, req, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def updatePermissionGroup(
    permissionIri: PermissionIri,
    request: ChangePermissionGroupApiRequestADM,
  ): Task[PermissionGetResponseADM] =
    for {
      uuid   <- Random.nextUUID
      result <- responder.updatePermissionsGroup(permissionIri, request.forGroup, uuid)
      ext    <- format.toExternal(result)
    } yield ext

  def getPermissionsDaopByProjectIri(
    candidate: ProjectCandidate,
  )(projectIri: ProjectIri): Task[DefaultObjectAccessPermissionsForProjectGetResponseADM] =
    for {
      grant  <- auth.authorizeProjectAdminById(candidate, projectIri)
      result <- responder.getPermissionsDaopByProjectIri(grant.project.id)
      ext    <- format.toExternal(result)
    } yield ext

  def getPermissionsApByProjectAndGroupIri(candidate: ProjectCandidate)(
    projectIri: ProjectIri,
    groupIri: GroupIri,
  ): Task[AdministrativePermissionGetResponseADM] =
    for {
      grant  <- auth.authorizeProjectAdminById(candidate, projectIri)
      result <-
        administrativePermissionService
          .findByGroupAndProject(groupIri, grant.project.id)
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
