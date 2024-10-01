/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.domain.AuthScope.ScopeValue.*
import swiss.dasch.domain.{AuthScope, ProjectShortcode}
import swiss.dasch.test.SpecConfigurations
import zio.*
import zio.test.*

object AuthorizationHandlerSpec extends ZIOSpecDefault {
  val sessionWith       = Principal("blank subject", _)
  val projectShortcode1 = ProjectShortcode.unsafeFrom("1234")
  val projectShortcode2 = ProjectShortcode.unsafeFrom("5678")
  val sessionEmpty      = sessionWith(AuthScope.Empty)
  val sessionRead       = sessionWith(AuthScope.from(Read(projectShortcode1)))
  val sessionWrite      = sessionWith(AuthScope.from(Write(projectShortcode1)))
  val sessionAdmin      = sessionWith(AuthScope.from(Admin))

  val handler = ZIO.serviceWithZIO[AuthorizationHandler]

  val specWithAuth =
    suite("should handle auth")(
      test("should check admin") {
        for {
          error1 <- handler(_.ensureAdminScope(sessionEmpty)).either
          error2 <- handler(_.ensureAdminScope(sessionRead)).either
          error3 <- handler(_.ensureAdminScope(sessionWrite)).either
        } yield {
          assertTrue(
            error1 == Left(ApiProblem.Unauthorized("Admin permissions required.")),
            error2 == Left(ApiProblem.Unauthorized("Admin permissions required.")),
            error3 == Left(ApiProblem.Unauthorized("Admin permissions required.")),
          )
        }
      },
      test("should authorize project read with read or admin") {
        for {
          error1 <- handler(_.ensureProjectReadable(sessionEmpty, projectShortcode1)).either
          error2 <- handler(_.ensureProjectReadable(sessionRead, projectShortcode2)).either
          error3 <- handler(_.ensureProjectReadable(sessionWrite, projectShortcode2)).either
          error4 <- handler(_.ensureProjectReadable(sessionRead, projectShortcode1)).either
          error5 <- handler(_.ensureProjectReadable(sessionWrite, projectShortcode1)).either
          error6 <- handler(_.ensureProjectReadable(sessionAdmin, projectShortcode1)).either
        } yield {
          assertTrue(
            error1 == Left(ApiProblem.Unauthorized("No project access.")),
            error2 == Left(ApiProblem.Unauthorized("No project access.")),
            error3 == Left(ApiProblem.Unauthorized("No project access.")),
            error4 == Right(()),
            error5 == Right(()),
            error6 == Right(()),
          )
        }
      },
    ).provide(
      AuthorizationHandlerLive.layer,
      SpecConfigurations.jwtConfigLayer,
    )

  val specDisabledAuth =
    suite("should disable auth") {
      test("should authorize anyone") {
        ZIO.serviceWithZIO[AuthorizationHandler](_.ensureAdminScope(sessionEmpty)).as(assertTrue(true))
      }
    }.provide(
      AuthorizationHandlerLive.layer,
      SpecConfigurations.jwtConfigDisableAuthLayer,
    )

  val spec =
    specWithAuth + specDisabledAuth
}
