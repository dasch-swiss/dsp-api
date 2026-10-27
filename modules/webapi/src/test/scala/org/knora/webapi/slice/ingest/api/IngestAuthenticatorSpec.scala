/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.infrastructure.InfrastructureModule
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.infrastructure.Scope
import org.knora.webapi.slice.infrastructure.ScopeValue
import org.knora.webapi.slice.ingest.api.IngestAuthenticationError.*
import org.knora.webapi.slice.ingest.testutil.SpecConfigurations
import org.knora.webapi.slice.ingest.testutil.SpecJwtTokens.*

@RunWith(classOf[DspZTestJUnitRunner])
class IngestAuthenticatorSpec extends ZIOSpecDefault {

  private def authenticate(token: String) = ZIO.serviceWithZIO[IngestAuthenticator](_.authenticate(token))

  private val shortcode = Shortcode.unsafeFrom("2345")

  val spec = suite("IngestAuthenticator")(
    test("should extract the scope from the contents") {
      for {
        token  <- tokenWithScope("write:project:2345")
        result <- authenticate(token)
      } yield assertTrue(result == IngestPrincipal("some-subject", Scope(Set(ScopeValue.Write(shortcode))), token))
    },
    test("should ignore unknown scope values") {
      for {
        token  <- tokenWithScope("I once saw a duck")
        result <- authenticate(token)
      } yield assertTrue(result == IngestPrincipal("some-subject", Scope.empty, token))
    },
    test("should fail with InvalidContents on a malformed project shortcode in the scope") {
      for {
        token  <- tokenWithScope("read:project:12")
        result <- authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(NonEmptyChunk(InvalidContents("Invalid contents: Shortcode is invalid: 12"))),
      )
    },
    test("should verify a valid token") {
      for {
        token  <- validToken()
        result <- authenticate(token)
      } yield assertTrue(result == IngestPrincipal("some-subject", Scope.empty, token))
    },
    test("should fail an expired token with a JwtProblem") {
      for {
        expiration <- Clock.instant.map(_.minusSeconds(3600))
        token      <- expiredToken(expiration)
        result     <- authenticate(token).exit
      } yield assertTrue(result == Exit.fail(NonEmptyChunk(JwtProblem("JWT token has expired"))))
    },
    test("should fail a malformed token with a JwtProblem") {
      for {
        result <- authenticate("invalid-token").exit
      } yield assertTrue(result == Exit.fail(NonEmptyChunk(JwtProblem("Invalid JWT format: expected 3 parts"))))
    },
    test("should fail a token with an invalid signature with a JwtProblem") {
      for {
        token  <- tokenWithInvalidSignature()
        result <- authenticate(token).exit
      } yield assertTrue(result == Exit.fail(NonEmptyChunk(JwtProblem("Invalid JWT signature"))))
    },
    test("should fail a token without the ingest audience") {
      for {
        token  <- tokenWithInvalidAudience()
        result <- authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(
          NonEmptyChunk(InvalidAudience("Invalid audience: expected https://dsp-ingest.dev.dasch.swiss")),
        ),
      )
    },
    test("should fail a token with a foreign issuer") {
      for {
        token  <- tokenWithInvalidIssuer()
        result <- authenticate(token).exit
      } yield assertTrue(
        result == Exit.fail(NonEmptyChunk(InvalidIssuer("Invalid issuer: expected https://admin.dev.dasch.swiss"))),
      )
    },
    test("should fail a token without subject") {
      for {
        token  <- tokenWithMissingSubject()
        result <- authenticate(token).exit
      } yield assertTrue(result == Exit.fail(NonEmptyChunk(SubjectMissing("Subject is missing."))))
    },
    test("should report every failed claim together") {
      for {
        token  <- createToken(issuer = Some("invalid-issuer"), subject = None, audience = Some(Set("other")))
        result <- authenticate(token).either
      } yield assertTrue(
        result.left.map(_.map(_.getClass.getSimpleName).toSet) == Left(
          Set("InvalidIssuer", "InvalidAudience", "SubjectMissing"),
        ),
      )
    },
    test("should accept a token minted by JwtService.createJwt with its scope") {
      val userIri = UserIri.unsafeFrom("http://rdfh.ch/users/root")
      for {
        jwt    <- ZIO.serviceWithZIO[JwtService](_.createJwt(userIri, Scope.write(shortcode)))
        result <- authenticate(jwt.jwtString)
      } yield assertTrue(result == IngestPrincipal(userIri.value, Scope.write(shortcode), jwt.jwtString))
    },
    test("should accept a service token whose subject is the issuer and whose audience is ingest only") {
      for {
        token  <- serviceToken()
        result <- authenticate(token)
      } yield assertTrue(result == IngestPrincipal("https://admin.dev.dasch.swiss", Scope.admin, token))
    },
  ).provide(SpecConfigurations.jwtConfigLayer, IngestAuthenticator.layer, InfrastructureModule.layer) @@
    TestAspect.withLiveClock
}
