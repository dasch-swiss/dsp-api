/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.model.StatusCode
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.EndpointOutput
import sttp.tapir.PublicEndpoint
import sttp.tapir.Validator
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.model.UsernamePassword
import sttp.tapir.ztapir.*
import zio.*
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import dsp.errors.*
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.AnonymousUser
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.security.Authenticator

final case class BaseEndpoints(authenticator: Authenticator) {

  private val errorOutputs: EndpointOutput.OneOf[Throwable, Throwable] =
    oneOf[Throwable](
      // client errors
      oneOfVariant[NotFoundException](statusCode(StatusCode.NotFound).and(jsonBody[NotFoundException])),
      oneOfVariant[BadRequestException](statusCode(StatusCode.BadRequest).and(jsonBody[BadRequestException])),
      oneOfVariant[EditConflictException](
        statusCode(StatusCode.BadRequest).and(jsonBody[EditConflictException]),
      ),
      oneOfVariant[ConflictException](statusCode(StatusCode.Conflict).and(jsonBody[ConflictException])),
      oneOfVariant[OntologyConstraintException](
        statusCode(StatusCode.BadRequest).and(jsonBody[OntologyConstraintException]),
      ),
      oneOfVariant[ValidationException](statusCode(StatusCode.BadRequest).and(jsonBody[ValidationException])),
      oneOfVariant[DuplicateValueException](statusCode(StatusCode.BadRequest).and(jsonBody[DuplicateValueException])),
      oneOfVariant[GravsearchException](statusCode(StatusCode.BadRequest).and(jsonBody[GravsearchException])),
      // security
      oneOfVariant[BadCredentialsException](statusCode(StatusCode.Unauthorized).and(jsonBody[BadCredentialsException])),
      oneOfVariant[ForbiddenException](statusCode(StatusCode.Forbidden).and(jsonBody[ForbiddenException])),
      // catch-all for any unhandled error (e.g. TriplestoreTimeoutException, other InternalServerException subtypes)
      oneOfDefaultVariant(
        statusCode(StatusCode.InternalServerError).and(
          jsonBody[BaseEndpoints.ErrorResponse]
            .map[Throwable](er => new Exception(er.message))(_ => BaseEndpoints.ErrorResponse("Internal server error")),
        ),
      ),
    )

  val publicEndpoint: PublicEndpoint[Unit, Throwable, Unit, Any] = endpoint.errorOut(errorOutputs)

  private type SecurityIn = (Option[String], Option[UsernamePassword])
  private val endpointWithBearerBasicAuthOptional = endpoint
    .errorOut(errorOutputs)
    .securityIn(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
    .securityIn(auth.basic[Option[UsernamePassword]](WWWAuthenticateChallenge.basic("realm")))

  val securedEndpoint: ZPartialServerEndpoint[Any, SecurityIn, User, Unit, Throwable, Unit, Any] =
    endpointWithBearerBasicAuthOptional.zServerSecurityLogic {
      case (Some(jwtToken), _) => authenticateJwt(jwtToken)
      case (_, Some(basic))    => authenticateBasic(basic)
      case _                   => ZIO.fail(BadCredentialsException("No credentials provided."))
    }

  val withUserEndpoint: ZPartialServerEndpoint[Any, SecurityIn, User, Unit, Throwable, Unit, Any] =
    endpointWithBearerBasicAuthOptional.zServerSecurityLogic {
      case (Some(bearer), _) => authenticateJwt(bearer)
      case (_, Some(basic))  => authenticateBasic(basic)
      case _                 => ZIO.succeed(AnonymousUser)
    }

  /**
   * A narrowing of [[securedEndpoint]] that accepts a bearer JWT and nothing else -- no HTTP basic, and (like every
   * endpoint here) no cookie. Use it for a route where accepting basic credentials would be a liability rather than a
   * convenience.
   *
   * Two properties make the narrowing worth having. First, basic authentication runs a bcrypt verification at the
   * configured strength on **every** call, and because the framework evaluates an endpoint's security logic before its
   * server logic, that cost sits upstream of any bound the server logic establishes -- so on a route that
   * deliberately bounds its own work, basic would be the one unbounded part. Second, omitting the basic security
   * input also omits the `WWW-Authenticate: Basic` challenge, which is worth keeping off a route whose existence the
   * generated API documentation advertises.
   *
   * The absence of a cookie input is what keeps such a route CSRF-safe: this API's CORS is reflected-origin with
   * credentials allowed, so a cookie credential here would be rideable from any origin. Treat that as a tested
   * invariant, not a convention.
   */
  val bearerSecuredEndpoint: ZPartialServerEndpoint[Any, Option[String], User, Unit, Throwable, Unit, Any] =
    endpoint
      .errorOut(errorOutputs)
      .securityIn(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
      .zServerSecurityLogic {
        case Some(jwtToken) => authenticateJwt(jwtToken)
        case _              => ZIO.fail(BadCredentialsException("No credentials provided."))
      }

  private def authenticateJwt(token: String): IO[BadCredentialsException, User] =
    authenticator.authenticate(token).orElseFail(BadCredentialsException("Invalid credentials."))

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

  private[api] final case class ErrorResponse(message: String)
  private[api] object ErrorResponse {
    implicit val codec: JsonCodec[ErrorResponse] = DeriveJsonCodec.gen[ErrorResponse]
  }
}
