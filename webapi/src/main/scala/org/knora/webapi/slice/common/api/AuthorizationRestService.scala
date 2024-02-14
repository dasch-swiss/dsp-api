/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import zio.*
import zio.macros.accessible

import dsp.errors.ForbiddenException
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.common.api.AuthorizationRestService.isActive
import org.knora.webapi.slice.common.api.AuthorizationRestService.isSystemAdmin
import org.knora.webapi.slice.common.api.AuthorizationRestService.isSystemAdminOrProjectAdminInAnyProject
import org.knora.webapi.slice.common.api.AuthorizationRestService.isSystemAdminSystemUserOrProjectAdmin
import org.knora.webapi.slice.common.api.AuthorizationRestService.isSystemAdminSystemUserOrProjectAdminInAnyProject
import org.knora.webapi.slice.common.api.AuthorizationRestService.isSystemOrProjectAdmin

/**
 * Provides methods for checking permissions.
 * This service is used by the REST API services.
 * All `ensure...` methods fail with a [[ForbiddenException]] if the user is not active or the respective check fails.
 *
 * @see [[AuthorizationRestServiceLive]].
 */
@accessible
trait AuthorizationRestService {

  /**
   * Checks if the user is a system administrator.
   * Checks if the user is active.
   *
   * @param user The [[User]] to check.
   * @return [[Unit]] if the user is active and a system administrator.
   *         Fails with a [[ForbiddenException]] otherwise.
   */
  def ensureSystemAdmin(user: User): IO[ForbiddenException, Unit]
  def ensureSystemAdminSystemUserOrProjectAdminInAnyProject(user: User): IO[ForbiddenException, Unit]
  def ensureSystemAdminOrProjectAdmin(user: User, project: ProjectIri): IO[ForbiddenException, KnoraProject]

  /**
   * Checks if the user is a system or project administrator.
   * Checks if the user is active.
   *
   * @param user    The [[User]] to check.
   * @param project The [[KnoraProject]] to check.
   * @return [[Unit]] if the user is active and is a system or project administrator.
   *         Fails with a [[ForbiddenException]] otherwise.
   */
  def ensureSystemAdminOrProjectAdmin(user: User, project: KnoraProject): IO[ForbiddenException, Unit]

  def ensureSystemAdminSystemUserOrProjectAdmin(
    user: User,
    project: KnoraProject
  ): IO[ForbiddenException, Unit]

  def ensureSystemAdminOrProjectAdminInAnyProject(requestingUser: User): IO[ForbiddenException, Unit]
}

/**
 * Provides helper methods for checking permissions.
 * All functions are pure.
 * Functions do not check if the user is active.
 */
object AuthorizationRestService {
  def isActive(userADM: User): Boolean                           = userADM.status
  def isSystemAdmin(user: User): Boolean                         = user.permissions.isSystemAdmin
  def isSystemUser(user: User): Boolean                          = user.isSystemUser
  def isProjectAdmin(user: User, project: KnoraProject): Boolean = user.permissions.isProjectAdmin(project.id.value)
  def isSystemOrProjectAdmin(project: KnoraProject)(userADM: User): Boolean =
    isSystemAdmin(userADM) || isProjectAdmin(userADM, project)
  def isSystemAdminSystemUserOrProjectAdmin(project: KnoraProject)(userADM: User): Boolean =
    isSystemUser(userADM) || isSystemAdmin(userADM) || isProjectAdmin(userADM, project)
  def isSystemAdminOrProjectAdminInAnyProject(user: User): Boolean =
    isSystemAdmin(user) || user.permissions.isProjectAdminInAnyProject()
  def isSystemAdminSystemUserOrProjectAdminInAnyProject(user: User): Boolean =
    isSystemUser(user) || isSystemAdmin(user) || user.permissions.isProjectAdminInAnyProject()
}

final case class AuthorizationRestServiceLive(projectRepo: KnoraProjectRepo) extends AuthorizationRestService {
  override def ensureSystemAdmin(user: User): IO[ForbiddenException, Unit] = {
    lazy val msg =
      s"You are logged in with username '${user.username}', but only a system administrator has permissions for this operation."
    checkActiveUser(user, isSystemAdmin, msg)
  }

  private def checkActiveUser(
    user: User,
    condition: User => Boolean,
    errorMsg: String
  ): IO[ForbiddenException, Unit] =
    ensureIsActive(user) *> ZIO.fail(ForbiddenException(errorMsg)).unless(condition(user)).unit

  private def ensureIsActive(user: User): IO[ForbiddenException, Unit] = {
    lazy val msg = s"The account with username '${user.username}' is not active."
    ZIO.fail(ForbiddenException(msg)).unless(isActive(user)).unit
  }

  override def ensureSystemAdminOrProjectAdmin(
    user: User,
    projectIri: ProjectIri
  ): IO[ForbiddenException, KnoraProject] =
    for {
      project <- projectRepo
                   .findById(projectIri)
                   .orDie
                   .someOrFail(ForbiddenException(s"Project with IRI '${projectIri.value}' not found"))
      _ <- ensureSystemAdminOrProjectAdmin(user, project)
    } yield project

  override def ensureSystemAdminOrProjectAdmin(user: User, project: KnoraProject): IO[ForbiddenException, Unit] = {
    lazy val msg =
      s"You are logged in with username '${user.username}', but only a system administrator or project administrator has permissions for this operation."
    checkActiveUser(user, isSystemOrProjectAdmin(project), msg)
  }
  override def ensureSystemAdminSystemUserOrProjectAdmin(
    user: User,
    project: KnoraProject
  ): IO[ForbiddenException, Unit] = {
    lazy val msg =
      s"You are logged in with username '${user.username}', but only a system administrator, system user or project administrator has permissions for this operation."
    checkActiveUser(user, isSystemAdminSystemUserOrProjectAdmin(project), msg)
  }

  override def ensureSystemAdminOrProjectAdminInAnyProject(requestingUser: User): IO[ForbiddenException, Unit] = {
    val msg = "ProjectAdmin or SystemAdmin permissions are required."
    checkActiveUser(requestingUser, isSystemAdminOrProjectAdminInAnyProject, msg)
  }

  override def ensureSystemAdminSystemUserOrProjectAdminInAnyProject(user: User): IO[ForbiddenException, Unit] = {
    val msg = "ProjectAdmin or SystemAdmin permissions are required."
    checkActiveUser(user, isSystemAdminSystemUserOrProjectAdminInAnyProject, msg)
  }
}

object AuthorizationRestServiceLive {
  val layer = ZLayer.derive[AuthorizationRestServiceLive]
}
