/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.AuthenticationError.*
import swiss.dasch.api.SpecJwtTokens.*
import swiss.dasch.config.Configuration.JwtConfig
import swiss.dasch.domain.AuthScope.ScopeValue.*
import swiss.dasch.domain.{AuthScope, ProjectShortcode}
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.test.SpecConfigurations.jwtConfigLayer
import zio.*
import zio.test.{TestAspect, ZIOSpecDefault, assertTrue}

import java.time.temporal.ChronoUnit

object AuthServiceLiveSpec extends ZIOSpecDefault {
  val spec = suite("AuthServiceLive")(
    test("Should extract AuthScope from contents") {
      for {
        token  <- createToken(scope = Some("write:project:2345"))
        result <- AuthService.authenticate(token)
      } yield assertTrue(
        token.nonEmpty,
        result == Principal("some-subject", AuthScope.from(Write(ProjectShortcode.unsafeFrom("2345"))), token),
      )
    },
    test("Should validate contents") {
      for {
        token  <- createToken(scope = Some("I once saw a duck"))
        result <- AuthService.authenticate(token)
      } yield assertTrue(
        result == Principal("some-subject", AuthScope.Empty, token),
      )
    },
    test("A valid token should be verified") {
      for {
        token  <- validToken()
        result <- AuthService.authenticate(token)
      } yield assertTrue(token.nonEmpty, result == Principal("some-subject", jwtRaw = token))
    },
    test("An expired token should fail with a JwtProblem") {
      for {
        expiration <- Clock.instant.map(_.minusSeconds(3600))
        token      <- expiredToken(expiration)
        result     <- AuthService.authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(
          NonEmptyChunk(JwtProblem(s"The token is expired since ${expiration.truncatedTo(ChronoUnit.SECONDS)}")),
        ),
      )
    },
    test("An invalid token should fail with JwtProblem") {
      for {
        result <- AuthService.authenticate("invalid-token").exit
      } yield assertTrue(
        result == Exit.fail(
          NonEmptyChunk(
            JwtProblem("Expected token [invalid-token] to be composed of 2 or 3 parts separated by dots."),
          ),
        ),
      )
    },
    test("A token with invalid signature should fail with JwtProblem") {
      for {
        token  <- tokenWithInvalidSignature()
        result <- AuthService.authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(NonEmptyChunk(JwtProblem("Invalid signature for this token or wrong algorithm."))),
      )
    },
    test("A token with invalid audience should fail with JwtProblem") {
      for {
        token  <- tokenWithInvalidAudience()
        result <- AuthService.authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(
          NonEmptyChunk(InvalidAudience("Invalid audience: expected https://dsp-ingest.dev.dasch.swiss")),
        ),
      )
    },
    test("A token with invalid issuer should fail with JwtProblem") {
      for {
        token  <- tokenWithInvalidIssuer()
        result <- AuthService.authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(NonEmptyChunk(InvalidIssuer("Invalid issuer: expected https://admin.dev.dasch.swiss"))),
      )
    },
    test("A token without subject should fail with JwtProblem") {
      for {
        token  <- tokenWithMissingSubject()
        result <- AuthService.authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(NonEmptyChunk(SubjectMissing("Subject is missing."))),
      )
    },
  ).provide(jwtConfigLayer, AuthServiceLive.layer) @@ TestAspect.withLiveClock

}
