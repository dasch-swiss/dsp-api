/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiProblem.Unauthorized
import swiss.dasch.config.Configuration.JwtConfig
import zio.ZIO
import zio.ZLayer
import swiss.dasch.domain.ProjectShortcode

trait AuthorizationHandler {
  def ensureAdminScope(userSession: Principal): ZIO[Any, ApiProblem, Unit]
  def ensureProjectReadable(userSession: Principal, shortcode: ProjectShortcode): ZIO[Any, ApiProblem, Unit]
}

class AuthorizationHandlerLive extends AuthorizationHandler {
  def ensureAdminScope(userSession: Principal): ZIO[Any, ApiProblem, Unit] =
    ZIO.unless(userSession.scope.hasAdmin)(ZIO.fail(Unauthorized("Admin permissions required."))).as(())

  def ensureProjectReadable(userSession: Principal, shortcode: ProjectShortcode): ZIO[Any, ApiProblem, Unit] =
    ZIO.unless(userSession.scope.projectReadable(shortcode))(ZIO.fail(Unauthorized("No project access."))).as(())
}

object AuthorizationHandlerLive {
  val Empty: AuthorizationHandler = new AuthorizationHandler {
    def ensureAdminScope(userSession: Principal): ZIO[Any, ApiProblem, Unit] =
      ZIO.succeed(())

    def ensureProjectReadable(userSession: Principal, shortcode: ProjectShortcode): ZIO[Any, ApiProblem, Unit] =
      ZIO.succeed(())
  }

  val layer: ZLayer[JwtConfig, Any, AuthorizationHandler] =
    ZLayer.fromZIO(ZIO.serviceWithZIO[JwtConfig] { config =>
      ZIO
        .logWarning("Authorization is disabled => Development flag JWT_DISABLE_AUTH set to true.")
        .when(config.disableAuth) *>
        ZIO.succeed(if (config.disableAuth) Empty else new AuthorizationHandlerLive())
    })
}
