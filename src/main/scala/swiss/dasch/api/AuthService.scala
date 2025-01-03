/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import pdi.jwt.*
import swiss.dasch.api.AuthService.JwtContents
import swiss.dasch.config.Configuration.JwtConfig
import swiss.dasch.domain.AuthScope
import zio.*
import zio.json.*
import zio.prelude.Validation

trait AuthService {
  def authenticate(jwtToken: String): ZIO[Any, NonEmptyChunk[AuthenticationError], Principal]
}

object AuthService {
  def authenticate(token: String): ZIO[AuthService, NonEmptyChunk[AuthenticationError], Principal] =
    ZIO.serviceWithZIO[AuthService](_.authenticate(token))

  case class JwtContents(scope: Option[String] = None)

  implicit val JwtContentsCodec: JsonCodec[JwtContents] = DeriveJsonCodec.gen[JwtContents]
}

sealed trait AuthenticationError { def message: String }
object AuthenticationError {
  final case class JwtProblem(message: String)      extends AuthenticationError
  final case class InvalidContents(message: String) extends AuthenticationError
  final case class InvalidAudience(message: String) extends AuthenticationError
  final case class InvalidIssuer(message: String)   extends AuthenticationError
  final case class SubjectMissing(message: String)  extends AuthenticationError
  def jwtProblem(e: Throwable): AuthenticationError = JwtProblem(e.getMessage)
  def invalidContents(error: String): AuthenticationError =
    InvalidContents(s"Invalid contents: $error")
  def invalidAudience(jwtConfig: JwtConfig): AuthenticationError =
    InvalidAudience(s"Invalid audience: expected ${jwtConfig.audience}")
  def invalidIssuer(jwtConfig: JwtConfig): AuthenticationError =
    InvalidIssuer(s"Invalid issuer: expected ${jwtConfig.issuer}")
  def subjectMissing(): AuthenticationError =
    SubjectMissing(s"Subject is missing.")
}

final case class AuthServiceLive(jwtConfig: JwtConfig) extends AuthService {
  private val alg      = Seq(JwtAlgorithm.HS256)
  private val secret   = jwtConfig.secret
  private val audience = jwtConfig.audience
  private val issuer   = jwtConfig.issuer

  def authenticate(jwtString: String): IO[NonEmptyChunk[AuthenticationError], Principal] =
    if (jwtConfig.disableAuth) {
      if (jwtString == "intentionallyInvalid")
        ZIO.fail(NonEmptyChunk(AuthenticationError.JwtProblem("refused")))
      else
        ZIO.succeed(Principal("developer", AuthScope(Set(AuthScope.ScopeValue.Admin)), "fake jwt claim"))
    } else {
      ZIO
        .fromTry(JwtZIOJson.decode(jwtString, secret, alg))
        .mapError(e => NonEmptyChunk(AuthenticationError.jwtProblem(e)))
        .flatMap(verifyClaim(_, jwtString))
    }

  private def verifyClaim(
    claim: JwtClaim,
    claimLiteral: String,
  ): IO[NonEmptyChunk[AuthenticationError], Principal] = {
    val audVal = if (claim.audience.getOrElse(Set.empty).contains(audience)) { Validation.succeed(()) }
    else { Validation.fail(AuthenticationError.invalidAudience(jwtConfig)) }

    val issVal = if (claim.issuer.contains(issuer)) { Validation.succeed(()) }
    else { Validation.fail(AuthenticationError.invalidIssuer(jwtConfig)) }

    val subVal = Validation.fromEither(claim.subject.toRight(AuthenticationError.subjectMissing()))

    val authScope =
      Validation
        .fromEither(
          for {
            contents  <- Right(Some(claim.content).filter(_.nonEmpty))
            parsed    <- contents.map(_.fromJson[JwtContents]).getOrElse(Right(JwtContents()))
            authScope <- parsed.scope.map(AuthScope.parse).getOrElse(Right(AuthScope.Empty))
          } yield authScope,
        )
        .mapError(AuthenticationError.invalidContents)

    Validation
      .validateWith(authScope, issVal, audVal, subVal)((authScope, _, _, subject) =>
        Principal(subject, authScope, claimLiteral),
      )
      .toZIOParallelErrors
  }
}

object AuthServiceLive {
  val layer =
    ZLayer.fromZIO(ZIO.serviceWithZIO[JwtConfig] { config =>
      ZIO
        .logWarning("Authentication is disabled => Development flag JWT_DISABLE_AUTH set to true.")
        .when(config.disableAuth) *>
        ZIO.succeed(AuthServiceLive(config))
    })
}
