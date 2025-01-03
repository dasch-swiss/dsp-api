/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiProblem.Unauthorized
import swiss.dasch.config.Configuration.JwtConfig
import swiss.dasch.domain.ProjectShortcode
import zio.{IO, ZIO, ZLayer}

trait AuthorizationHandler {
  def ensureAdminScope(userSession: Principal): IO[ApiProblem, Unit]
  def ensureProjectReadable(userSession: Principal, shortcode: ProjectShortcode): IO[ApiProblem, Unit]
  def ensureProjectWritable(userSession: Principal, shortcode: ProjectShortcode): IO[ApiProblem, Unit]
}

class AuthorizationHandlerLive extends AuthorizationHandler {
  def ensureAdminScope(userSession: Principal): IO[ApiProblem, Unit] =
    ZIO.unless(userSession.scope.hasAdmin)(ZIO.fail(Unauthorized("Admin permissions required."))).unit

  def ensureProjectReadable(userSession: Principal, shortcode: ProjectShortcode): IO[ApiProblem, Unit] =
    ZIO.unless(userSession.scope.projectReadable(shortcode))(ZIO.fail(Unauthorized("No project access."))).unit

  def ensureProjectWritable(userSession: Principal, shortcode: ProjectShortcode): IO[ApiProblem, Unit] =
    ZIO.unless(userSession.scope.projectWritable(shortcode))(ZIO.fail(Unauthorized("No project access."))).unit
}

object AuthorizationHandlerLive {
  val Empty: AuthorizationHandler = new AuthorizationHandler {
    def ensureAdminScope(userSession: Principal): IO[ApiProblem, Unit]                                   = ZIO.unit
    def ensureProjectReadable(userSession: Principal, shortcode: ProjectShortcode): IO[ApiProblem, Unit] = ZIO.unit
    def ensureProjectWritable(userSession: Principal, shortcode: ProjectShortcode): IO[ApiProblem, Unit] = ZIO.unit
  }

  val layer: ZLayer[JwtConfig, Any, AuthorizationHandler] =
    ZLayer.fromZIO(ZIO.serviceWithZIO[JwtConfig] { config =>
      val isAuthDisabled = config.disableAuth
      ZIO
        .logWarning("Authorization is disabled => Development flag JWT_DISABLE_AUTH set to true.")
        .when(isAuthDisabled)
        .as(if (isAuthDisabled) Empty else new AuthorizationHandlerLive())
    })
}
