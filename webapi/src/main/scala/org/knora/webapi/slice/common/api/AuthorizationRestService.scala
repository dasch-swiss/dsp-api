/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import zio.*
import zio.macros.accessible

import dsp.errors.ForbiddenException
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.common.api.AuthorizationRestService.isActive
import org.knora.webapi.slice.common.api.AuthorizationRestService.isSystemAdmin
import org.knora.webapi.slice.common.api.AuthorizationRestService.isSystemAdminSystemUserOrProjectAdmin
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
   * @param user The [[UserADM]] to check.
   * @return [[Unit]] if the user is active and a system administrator.
   *         Fails with a [[ForbiddenException]] otherwise.
   */
  def ensureSystemAdmin(user: UserADM): IO[ForbiddenException, Unit]

  /**
   * Checks if the user is a system or project administrator.
   * Checks if the user is active.
   *
   * @param user    The [[UserADM]] to check.
   * @param project The [[KnoraProject]] to check.
   * @return [[Unit]] if the user is active and is a system or project administrator.
   *         Fails with a [[ForbiddenException]] otherwise.
   */
  def ensureSystemAdminOrProjectAdmin(user: UserADM, project: KnoraProject): IO[ForbiddenException, Unit]

  def ensureSystemAdminSystemUserOrProjectAdmin(
    user: UserADM,
    project: KnoraProject
  ): IO[ForbiddenException, Unit]
}

/**
 * Provides helper methods for checking permissions.
 * All functions are pure.
 * Functions do not check if the user is active.
 */
object AuthorizationRestService {
  def isActive(userADM: UserADM): Boolean                           = userADM.status
  def isSystemAdmin(user: UserADM): Boolean                         = user.permissions.isSystemAdmin
  def isSystemUser(user: UserADM): Boolean                          = user.isSystemUser
  def isProjectAdmin(user: UserADM, project: KnoraProject): Boolean = user.permissions.isProjectAdmin(project.id.value)
  def isSystemOrProjectAdmin(project: KnoraProject)(userADM: UserADM): Boolean =
    isSystemAdmin(userADM) || isProjectAdmin(userADM, project)
  def isSystemAdminSystemUserOrProjectAdmin(project: KnoraProject)(userADM: UserADM): Boolean =
    isSystemUser(userADM) || isSystemAdmin(userADM) || isProjectAdmin(userADM, project)
}

final case class AuthorizationRestServiceLive() extends AuthorizationRestService {
  override def ensureSystemAdmin(user: UserADM): IO[ForbiddenException, Unit] = {
    lazy val msg =
      s"You are logged in with username '${user.username}', but only a system administrator has permissions for this operation."
    checkActiveUser(user, isSystemAdmin, msg)
  }

  private def checkActiveUser(
    user: UserADM,
    condition: UserADM => Boolean,
    errorMsg: String
  ): ZIO[Any, ForbiddenException, Unit] =
    ensureIsActive(user) *> ZIO.fail(ForbiddenException(errorMsg)).unless(condition(user)).unit

  private def ensureIsActive(user: UserADM): IO[ForbiddenException, Unit] = {
    lazy val msg = s"The account with username '${user.username}' is not active."
    ZIO.fail(ForbiddenException(msg)).unless(isActive(user)).unit
  }

  override def ensureSystemAdminOrProjectAdmin(user: UserADM, project: KnoraProject): IO[ForbiddenException, Unit] = {
    lazy val msg =
      s"You are logged in with username '${user.username}', but only a system administrator or project administrator has permissions for this operation."
    checkActiveUser(user, isSystemOrProjectAdmin(project), msg)
  }
  override def ensureSystemAdminSystemUserOrProjectAdmin(
    user: UserADM,
    project: KnoraProject
  ): IO[ForbiddenException, Unit] = {
    lazy val msg =
      s"You are logged in with username '${user.username}', but only a system administrator, system user or project administrator has permissions for this operation."
    checkActiveUser(user, isSystemAdminSystemUserOrProjectAdmin(project), msg)
  }
}

object AuthorizationRestServiceLive {
  val layer: ULayer[AuthorizationRestService] = ZLayer.fromFunction(AuthorizationRestServiceLive.apply _)
}