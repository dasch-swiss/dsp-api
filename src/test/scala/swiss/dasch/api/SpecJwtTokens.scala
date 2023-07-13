/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import pdi.jwt.*
import pdi.jwt.exceptions.JwtException
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

object SpecJwtTokens {
  def validToken(): URIO[JwtConfig, String]                      = createToken()
  def expiredToken(expiration: Instant): URIO[JwtConfig, String] = createToken(expiration = Some(expiration))
  def tokenWithInvalidSignature(): URIO[JwtConfig, String]       = createToken(secret = Some("invalid-secret"))
  def tokenWithInvalidAudience(): URIO[JwtConfig, String]        = createToken(audience = Some(Set("invalid-audience")))
  def tokenWithInvalidIssuer(): URIO[JwtConfig, String]          = createToken(issuer = Some("invalid-issuer"))
  def createToken(
      issuer: Option[String] = None,
      subject: Option[String] = None,
      audience: Option[Set[String]] = None,
      expiration: Option[Instant] = None,
      secret: Option[String] = None,
    ): URIO[JwtConfig, String] =
    for {
      now       <- Clock.instant
      jwtConfig <- ZIO.service[JwtConfig]
      claim      = JwtClaim(
                     issuer = issuer.orElse(Some(jwtConfig.issuer)),
                     subject = subject.orElse(Some("some-subject")),
                     audience = audience.orElse(Some(Set(jwtConfig.audience))),
                     issuedAt = Some(now.getEpochSecond),
                     expiration = expiration.orElse(Some(now.plusSeconds(3600))).map(_.getEpochSecond),
                   )
    } yield JwtZIOJson.encode(claim, secret.getOrElse(jwtConfig.secret), JwtAlgorithm.HS256)
}
