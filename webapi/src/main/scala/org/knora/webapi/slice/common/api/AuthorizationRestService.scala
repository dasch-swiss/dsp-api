/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import zio._

import dsp.errors.ForbiddenException
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.api.AuthorizationRestService.isActive
import org.knora.webapi.slice.common.api.AuthorizationRestService.isSystemAdminOrProjectAdminInAnyProject
import org.knora.webapi.slice.common.api.AuthorizationRestService.isSystemAdminOrUser
import org.knora.webapi.slice.common.api.AuthorizationRestService.isSystemOrProjectAdmin

import org.knora.webapi.slice.admin.domain.model.KnoraProject

final case class AuthorizationRestService(
  knoraProjectService: KnoraProjectService,
  knoraGroupService: KnoraGroupService,
) {
  def ensureSystemAdmin(user: User): IO[ForbiddenException, Unit] = {
    lazy val msg =
      s"You are logged in with username '${user.username}', but only a system administrator has permissions for this operation."
    checkActiveUser(user, isSystemAdminOrUser, msg)
  }
  def ensureSystemAdminOrProjectAdminOfGroup(
    user: User,
    groupIri: GroupIri,
  ): IO[ForbiddenException, KnoraProject] =
    for {
      group <- knoraGroupService
                 .findById(groupIri)
                 .orDie
                 .someOrFail(ForbiddenException(s"Group with IRI '${groupIri.value}' not found"))
      projectIri <- ZIO
                      .succeed(group.belongsToProject)
                      .someOrFail(ForbiddenException(s"Group with IRI '${groupIri.value}' not found"))
      project <- ensureSystemAdminOrProjectAdmin(user, projectIri)
    } yield project

  def ensureSystemAdminOrProjectAdmin(
    user: User,
    projectIri: ProjectIri,
  ): IO[ForbiddenException, KnoraProject] =
    knoraProjectService
      .findById(projectIri)
      .orDie
      .someOrFail(ForbiddenException(s"Project with IRI '${projectIri.value}' not found"))
      .tap(ensureSystemAdminOrProjectAdmin(user, _))

  def ensureSystemAdminOrProjectAdmin(user: User, project: KnoraProject): IO[ForbiddenException, Unit] = {
    lazy val msg =
      s"You are logged in with username '${user.username}', but only a system administrator or project administrator has permissions for this operation."
    checkActiveUser(user, isSystemOrProjectAdmin(project), msg)
  }

  def ensureSystemAdminOrProjectAdminInAnyProject(requestingUser: User): IO[ForbiddenException, Unit] = {
    val msg = "ProjectAdmin or SystemAdmin permissions are required."
    checkActiveUser(requestingUser, isSystemAdminOrProjectAdminInAnyProject, msg)
  }

  private def checkActiveUser(
    user: User,
    condition: User => Boolean,
    errorMsg: String,
  ): IO[ForbiddenException, Unit] =
    ensureIsActive(user) *> ZIO.fail(ForbiddenException(errorMsg)).unless(condition(user)).unit

  private def ensureIsActive(user: User): IO[ForbiddenException, Unit] = {
    lazy val msg = s"The account with username '${user.username}' is not active."
    ZIO.fail(ForbiddenException(msg)).unless(isActive(user)).unit
  }
}

object AuthorizationRestService {
  def isActive(userADM: User): Boolean                           = userADM.status
  def isSystemAdminOrUser(user: User): Boolean                   = user.permissions.isSystemAdmin || user.isSystemUser
  def isProjectAdmin(user: User, project: KnoraProject): Boolean = user.permissions.isProjectAdmin(project.id.value)
  def isSystemOrProjectAdmin(project: KnoraProject)(userADM: User): Boolean =
    isSystemAdminOrUser(userADM) || isProjectAdmin(userADM, project)
  def isSystemAdminOrProjectAdminInAnyProject(user: User): Boolean =
    isSystemAdminOrUser(user) || user.permissions.isProjectAdminInAnyProject()

  val layer = ZLayer.derive[AuthorizationRestService]
}
