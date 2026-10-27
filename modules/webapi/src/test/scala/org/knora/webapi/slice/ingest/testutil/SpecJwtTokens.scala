/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.testutil

import zio.*
import zio.json.ast.Json

import java.nio.charset.StandardCharsets
import java.time.Instant

import org.knora.jwt.JwtClaim
import org.knora.jwt.JwtCodec
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.JwtConfig

/** Tokens signed with the spec's webapi JWT settings, shaped as dsp-api mints them for ingest. */
object SpecJwtTokens {
  type Env = JwtConfig & DspIngestConfig

  def validToken(): URIO[Env, String]                      = createToken()
  def adminToken(): URIO[Env, String]                      = createToken(scope = Some("admin"))
  def tokenWithScope(scope: String): URIO[Env, String]     = createToken(scope = Some(scope))
  def expiredToken(expiration: Instant): URIO[Env, String] = createToken(expiration = Some(expiration))
  def tokenWithInvalidSignature(): URIO[Env, String]       = createToken(secret = Some("invalid-secret"))
  def tokenWithInvalidAudience(): URIO[Env, String]        = createToken(audience = Some(Set("invalid-audience")))
  def tokenWithInvalidIssuer(): URIO[Env, String]          = createToken(issuer = Some("invalid-issuer"))
  def tokenWithMissingSubject(): URIO[Env, String]         = createToken(subject = None)
  def tokenWithSubject(subject: String): URIO[Env, String] = createToken(subject = Some(subject))
  def serviceToken(): URIO[Env, String]                    =
    ZIO.serviceWithZIO[JwtConfig](c => createToken(subject = c.issuer, scope = Some("admin")))

  def createToken(
    issuer: Option[String] = None,
    subject: Option[String] = Some("some-subject"),
    audience: Option[Set[String]] = None,
    expiration: Option[Instant] = None,
    secret: Option[String] = None,
    scope: Option[String] = None,
  ): URIO[Env, String] =
    for {
      now       <- Clock.instant
      jwtConfig <- ZIO.service[JwtConfig]
      ingest    <- ZIO.service[DspIngestConfig]
      claim      = JwtClaim(
                content = Json.Obj(scope.map(s => "scope" -> Json.Str(s)).toSeq*).toString,
                issuer = issuer.orElse(jwtConfig.issuer),
                subject = subject,
                audience = audience.orElse(Some(Set(ingest.audience))),
                issuedAt = Some(now.getEpochSecond),
                expiration = expiration.orElse(Some(now.plusSeconds(3600))).map(_.getEpochSecond),
              )
    } yield JwtCodec.encode(claim, secret.getOrElse(jwtConfig.secret).getBytes(StandardCharsets.UTF_8))
}
