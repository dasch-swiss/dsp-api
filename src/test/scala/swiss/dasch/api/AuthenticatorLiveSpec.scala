/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import pdi.jwt.*
import pdi.jwt.exceptions.JwtException
import swiss.dasch.api.SpecJwtTokens.*
import swiss.dasch.config.Configuration.{ JwtConfig, ServiceConfig }
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConfigurations.jwtConfigLayer
import zio.*
import zio.json.ast.Json
import zio.prelude.{ Validation, ZValidation }
import zio.test.{ TestAspect, ZIOSpecDefault, assertCompletes, assertTrue }

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.util.Try

object AuthenticatorLiveSpec extends ZIOSpecDefault {

  val spec = suite("AuthenticatorLive")(
    test("A valid token should be verified") {
      for {
        token <- validToken()
        json  <- Authenticator.authenticate(token)
      } yield assertTrue(token.nonEmpty, json != null)
    },
    test("An expired token should fail with a JwtProblem") {
      for {
        expiration <- Clock.instant.map(_.minusSeconds(3600))
        token      <- expiredToken(expiration)
        result     <- Authenticator.authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(
          NonEmptyChunk(JwtProblem(s"The token is expired since ${expiration.truncatedTo(ChronoUnit.SECONDS)}"))
        )
      )
    },
    test("An invalid token should fail with JwtProblem") {
      for {
        result <- Authenticator.authenticate("invalid-token").exit
      } yield assertTrue(
        result == Exit.fail(
          NonEmptyChunk(
            JwtProblem("Expected token [invalid-token] to be composed of 2 or 3 parts separated by dots.")
          )
        )
      )
    },
    test("A token with invalid signature should fail with JwtProblem") {
      for {
        token  <- tokenWithInvalidSignature()
        result <- Authenticator.authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(NonEmptyChunk(JwtProblem("Invalid signature for this token or wrong algorithm.")))
      )
    },
    test("A token with invalid audience should fail with JwtProblem") {
      for {
        token  <- tokenWithInvalidAudience()
        result <- Authenticator.authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(
          NonEmptyChunk(InvalidAudience("Invalid audience: expected https://dsp-ingest.dev.dasch.swiss"))
        )
      )
    },
    test("A token with invalid issuer should fail with JwtProblem") {
      for {
        token  <- tokenWithInvalidIssuer()
        result <- Authenticator.authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(NonEmptyChunk(InvalidIssuer("Invalid issuer: expected https://admin.dev.dasch.swiss")))
      )
    },
  ).provide(jwtConfigLayer, AuthenticatorLive.layer) @@ TestAspect.withLiveClock

}
