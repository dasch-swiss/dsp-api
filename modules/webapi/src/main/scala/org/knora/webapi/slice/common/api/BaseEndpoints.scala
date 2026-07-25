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

final case class BaseEndpoints(authenticator: Authenticator, authorization: AuthorizationRestService) {

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
   * endpoint here) no cookie -- and that authorizes the caller as a SystemAdmin **in the security logic**. Use it for a
   * route where accepting basic credentials would be a liability rather than a convenience, and where an unauthorized
   * caller must not be able to cause work.
   *
   * Both narrowings turn on the same framework ordering: an endpoint's security logic is evaluated before its request
   * body is decoded, and its server logic only afterwards.
   *
   *   - Authorizing here rather than in the server logic, as every other admin route does, is what makes the refusal
   *     land before the framework has *decoded* a single body byte. Note the bound this does and does not give: the
   *     zio-http server aggregates request bodies up to its own threshold before any tapir logic runs at all, so a
   *     body up to that size is buffered for every caller regardless. What moving the check here removes is
   *     everything downstream of it -- decoding, the endpoint's own body cap, and the server logic -- for a caller
   *     who is not a SystemAdmin. See `DspApiServer.maxAggregatedRequestBodySize`.
   *   - Basic authentication runs a bcrypt verification at the configured strength on **every** call, and that cost
   *     would likewise sit upstream of any bound the server logic establishes -- so on a route that deliberately
   *     bounds its own work, basic would be the one unbounded part. Omitting the basic security input also omits the
   *     `WWW-Authenticate: Basic` challenge, worth keeping off a route whose existence the generated API documentation
   *     advertises.
   *
   * The absence of a cookie input is what keeps such a route CSRF-safe: this API's CORS is reflected-origin with
   * credentials allowed, so a cookie credential here would be rideable from any origin. Treat that as a tested
   * invariant, not a convention.
   *
   * @param onRejected run when the caller is turned away, with `Some(user)` if authentication succeeded and only the
   *                   SystemAdmin check failed, and `None` if authentication itself did. A failure in the security
   *                   logic carries only the exception onwards, so a surface that must attribute rejected attempts
   *                   would otherwise lose both the identity and the distinction; this is where it still has them.
   *                   The raw token is deliberately not passed: it is a live credential and must not reach a log.
   */
  def bearerSystemAdminEndpoint(
    onRejected: Option[User] => UIO[Unit],
  ): ZPartialServerEndpoint[Any, Option[String], User, Unit, Throwable, Unit, Any] =
    endpoint
      .errorOut(errorOutputs)
      .securityIn(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
      .zServerSecurityLogic {
        case Some(jwtToken) =>
          asTypedFailure(
            authenticateJwt(jwtToken)
              .tapError(_ => onRejected(None))
              .flatMap(user => authorization.ensureSystemAdmin(user).tapError(_ => onRejected(Some(user))).as(user)),
          )
        case _ => onRejected(None) *> ZIO.fail(BadCredentialsException("No credentials provided."))
      }

  /**
   * Turns a defect thrown out of the security logic into a typed failure, so it is answered through the endpoint's
   * own error output instead of escaping to the server's outer exception handling.
   *
   * The status is a `500` either way -- both render the shared "Internal server error" body -- but the route the
   * response takes is not the same. A defect bypasses `onSecurityFailure`, and with it the interceptor that marks a
   * response produced before the request body was read as the last on its connection; on a streamed body that leaves
   * the connection unreadable and the client's next request on it hangs. Failing typed keeps the rejection inside the
   * hook that knows about that. The defect is logged here because it is a genuine bug and must not become quieter for
   * having been converted.
   */
  private def asTypedFailure(zio: IO[Throwable, User]): IO[Throwable, User] =
    zio.catchAllDefect(defect =>
      ZIO.logErrorCause("Defect in the security logic", Cause.die(defect)) *>
        ZIO.fail(AssertionException("Defect in the security logic", defect)),
    )

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
