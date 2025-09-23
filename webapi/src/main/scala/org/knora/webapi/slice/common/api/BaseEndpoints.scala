/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.model.StatusCode
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.ztapir.*
import sttp.tapir.{EndpointOutput, PublicEndpoint, Validator}
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.model.UsernamePassword
import zio.ZIO
import zio.ZLayer

import scala.concurrent.Future

import dsp.errors.*
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.AnonymousUser
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.security.Authenticator

final case class BaseEndpoints(authenticator: Authenticator) {

  private val errorOutputs =
    oneOf[Throwable](
      // default
      oneOfVariant[NotFoundException](statusCode(StatusCode.NotFound).and(jsonBody[NotFoundException])),
      oneOfVariant[BadRequestException](statusCode(StatusCode.BadRequest).and(jsonBody[BadRequestException])),
      oneOfVariant[EditConflictException](
        statusCode(StatusCode.BadRequest).and(jsonBody[EditConflictException]),
      ),
      oneOfVariant[OntologyConstraintException](
        statusCode(StatusCode.BadRequest).and(jsonBody[OntologyConstraintException]),
      ),
      oneOfVariant[ValidationException](statusCode(StatusCode.BadRequest).and(jsonBody[ValidationException])),
      oneOfVariant[DuplicateValueException](statusCode(StatusCode.BadRequest).and(jsonBody[DuplicateValueException])),
      oneOfVariant[GravsearchException](statusCode(StatusCode.BadRequest).and(jsonBody[GravsearchException])),
      // plus security
      oneOfVariant[BadCredentialsException](statusCode(StatusCode.Unauthorized).and(jsonBody[BadCredentialsException])),
      oneOfVariant[ForbiddenException](statusCode(StatusCode.Forbidden).and(jsonBody[ForbiddenException])),
    )

  val publicEndpoint = endpoint.errorOut(errorOutputs)

  private val endpointWithBearerCookieBasicAuthOptional =
    endpoint
      .errorOut(errorOutputs)
      .securityIn(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
      .securityIn(cookie[Option[String]](authenticator.calculateCookieName()))
      .securityIn(auth.basic[Option[UsernamePassword]](WWWAuthenticateChallenge.basic("realm")))

  val securedEndpoint = endpointWithBearerCookieBasicAuthOptional.zServerSecurityLogic {
    case (Some(jwtToken), _, _) => authenticateJwt(jwtToken)
    case (_, Some(cookie), _)   => authenticateJwt(cookie)
    case (_, _, Some(basic))    => authenticateBasic(basic)
    case _                      => ZIO.fail(BadCredentialsException("No credentials provided."))
  }

  val withUserEndpoint = endpointWithBearerCookieBasicAuthOptional.zServerSecurityLogic {
    case (Some(jwtToken), _, _) => authenticateJwt(jwtToken)
    case (_, Some(cookie), _)   => authenticateJwt(cookie)
    case (_, _, Some(basic))    => authenticateBasic(basic)
    case _                      => ZIO.succeed(AnonymousUser)
  }

  private def authenticateJwt(jwtToken: String): IO[BadCredentialsException, User] =
    authenticator.authenticate(jwtToken).orElseFail(BadCredentialsException("Invalid credentials."))

  private def authenticateBasic(basic: UsernamePassword): IO[BadCredentialsException, User] =
    for {
      email <- ZIO
                 .fromEither(Email.from(basic.username))
                 .orElseFail(BadCredentialsException("Invalid credentials, email address expected."))
      password <- ZIO
                    .fromOption(basic.password)
                    .orElseFail(BadCredentialsException("Invalid credentials, missing password."))
      userAndJwt <- authenticator
                      .authenticate(email, password)
                      .orElseFail(BadCredentialsException("Invalid credentials."))
      (user, _) = userAndJwt
    } yield user
}

object BaseEndpoints {
  val layer = ZLayer.derive[BaseEndpoints]
}
