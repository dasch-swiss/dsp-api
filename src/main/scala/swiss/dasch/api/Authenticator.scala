/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.config.Configuration.JwtConfig
import zio.*
import zio.prelude.Validation
import pdi.jwt.*
import pdi.jwt.exceptions.JwtException
import zio.http.{ HttpAppMiddleware, RequestHandlerMiddleware }

trait Authenticator  {
  def authenticate(token: String): ZIO[Any, NonEmptyChunk[AuthenticationError], JwtClaim]
}
object Authenticator {

  val middleware: RequestHandlerMiddleware[Nothing, Authenticator with JwtConfig, Nothing, Any] =
    HttpAppMiddleware.bearerAuthZIO(token =>
      for {
        isAuthDisabled  <- ZIO.serviceWith[JwtConfig](_.disableAuth)
        isAuthenticated <- if (isAuthDisabled) { ZIO.succeed(true) }
                           else { Authenticator.authenticate(token).fold(_ => false, _ => true) }
      } yield isAuthenticated
    )

  def authenticate(token: String): ZIO[Authenticator, NonEmptyChunk[AuthenticationError], JwtClaim] =
    ZIO.serviceWithZIO[Authenticator](_.authenticate(token))
}

sealed trait AuthenticationError { def message: String }
final case class JwtProblem(message: String)      extends AuthenticationError
final case class InvalidAudience(message: String) extends AuthenticationError
final case class InvalidIssuer(message: String)   extends AuthenticationError
object AuthenticationError {
  def jwtProblem(e: JwtException): AuthenticationError           = JwtProblem(e.getMessage)
  def invalidAudience(jwtConfig: JwtConfig): AuthenticationError =
    InvalidAudience(s"Invalid audience: expected ${jwtConfig.audience}")
  def invalidIssuer(jwtConfig: JwtConfig): AuthenticationError   =
    InvalidIssuer(s"Invalid issuer: expected ${jwtConfig.issuer}")
}

final case class AuthenticatorLive(jwtConfig: JwtConfig) extends Authenticator {
  private val alg      = Seq(JwtAlgorithm.HS256)
  private val secret   = jwtConfig.secret
  private val audience = jwtConfig.audience
  private val issuer   = jwtConfig.issuer

  def authenticate(jwtString: String): IO[NonEmptyChunk[AuthenticationError], JwtClaim] =
    for {
      claim <- ZIO
                 .fromTry(JwtZIOJson.decode(jwtString, secret, alg))
                 .refineOrDie { case e: JwtException => NonEmptyChunk(AuthenticationError.jwtProblem(e)) }
      _     <- verifyClaim(claim)
    } yield claim

  private def verifyClaim(claim: JwtClaim): IO[NonEmptyChunk[AuthenticationError], JwtClaim] =
    val audVal = if (claim.audience.getOrElse(Set.empty).contains(audience)) { Validation.succeed(claim) }
    else { Validation.fail(AuthenticationError.invalidAudience(jwtConfig)) }

    val issVal = if (claim.issuer.contains(issuer)) { Validation.succeed(claim) }
    else { Validation.fail(AuthenticationError.invalidIssuer(jwtConfig)) }

    ZIO.fromEither(Validation.validateWith(issVal, audVal)((_, _) => claim).toEither)
}

object AuthenticatorLive {
  val layer = ZLayer.fromFunction(AuthenticatorLive.apply _)
}
