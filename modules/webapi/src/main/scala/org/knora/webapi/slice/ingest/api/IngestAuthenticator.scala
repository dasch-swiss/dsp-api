/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import zio.*
import zio.json.*
import zio.prelude.Validation

import java.nio.charset.StandardCharsets

import org.knora.jwt.JwtClaim
import org.knora.jwt.JwtCodec
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.JwtConfig
import org.knora.webapi.slice.infrastructure.Scope

/** The caller of an ingest route: the token's subject, its scope and the raw token. */
final case class IngestPrincipal(subject: String, scope: Scope, jwtRaw: String)

sealed trait IngestAuthenticationError { def message: String }
object IngestAuthenticationError       {
  final case class JwtProblem(message: String)      extends IngestAuthenticationError
  final case class InvalidContents(message: String) extends IngestAuthenticationError
  final case class InvalidAudience(message: String) extends IngestAuthenticationError
  final case class InvalidIssuer(message: String)   extends IngestAuthenticationError
  final case class SubjectMissing(message: String)  extends IngestAuthenticationError
}

/**
 * Accepts an HS256 token signed with dsp-api's secret whose issuer is dsp-api's, whose audience contains the ingest
 * audience and which has a subject, and reads its optional `scope` claim. Unlike webapi's `Authenticator` it resolves
 * no user and does not consult the logout cache: the subject of a service token is the issuer, not a user IRI.
 */
final class IngestAuthenticator(jwtConfig: JwtConfig, dspIngestConfig: DspIngestConfig) {
  import IngestAuthenticationError.*

  private val secretBytes = jwtConfig.secret.getBytes(StandardCharsets.UTF_8)
  private val audience    = dspIngestConfig.audience
  private val issuer      = jwtConfig.issuer

  def authenticate(jwtString: String): IO[NonEmptyChunk[IngestAuthenticationError], IngestPrincipal] =
    ZIO
      .fromTry(JwtCodec.decode(jwtString, secretBytes))
      .mapError(e => NonEmptyChunk(JwtProblem(e.getMessage)))
      .flatMap(verifyClaim(_, jwtString))

  private def verifyClaim(
    claim: JwtClaim,
    claimLiteral: String,
  ): IO[NonEmptyChunk[IngestAuthenticationError], IngestPrincipal] = {
    val audVal =
      if (claim.audience.getOrElse(Set.empty).contains(audience)) Validation.succeed(())
      else Validation.fail(InvalidAudience(s"Invalid audience: expected $audience"))

    val issVal =
      if (issuer.isDefined && claim.issuer == issuer) Validation.succeed(())
      else Validation.fail(InvalidIssuer(s"Invalid issuer: expected ${issuer.getOrElse("")}"))

    val subVal = Validation.fromEither(claim.subject.toRight(SubjectMissing("Subject is missing.")))

    val scopeVal =
      Validation
        .fromEither(
          for {
            contents <- Right(Some(claim.content).filter(_.nonEmpty))
            parsed   <-
              contents.fold(Right(IngestAuthenticator.JwtContents()))(_.fromJson[IngestAuthenticator.JwtContents])
            scope <- parsed.scope.fold(Right(Scope.empty))(Scope.parse)
          } yield scope,
        )
        .mapError(e => InvalidContents(s"Invalid contents: $e"))

    Validation
      .validateWith(scopeVal, issVal, audVal, subVal)((scope, _, _, subject) =>
        IngestPrincipal(subject, scope, claimLiteral),
      )
      .toZIOParallelErrors
  }
}

object IngestAuthenticator {
  private final case class JwtContents(scope: Option[String] = None)
  private given JsonCodec[JwtContents] = DeriveJsonCodec.gen[JwtContents]

  val layer: URLayer[JwtConfig & DspIngestConfig, IngestAuthenticator] = ZLayer.derive[IngestAuthenticator]
}
