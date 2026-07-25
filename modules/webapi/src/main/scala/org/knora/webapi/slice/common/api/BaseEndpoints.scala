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
   * @param onRejected run exactly once when the caller is turned away, with the reason. A failure in the security
   *                   logic carries only the exception onwards, so a surface that must attribute rejected attempts
   *                   would otherwise lose both the identity and the distinction; this is where it still has them.
   *                   The raw token is deliberately not passed: it is a live credential and must not reach a log.
   */
  def bearerSystemAdminEndpoint(
    onRejected: BaseEndpoints.Rejection => UIO[Unit],
  ): ZPartialServerEndpoint[Any, Option[String], User, Unit, Throwable, Unit, Any] =
    endpoint
      .errorOut(errorOutputs)
      .securityIn(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
      .zServerSecurityLogic {
        case Some(jwtToken) =>
          attributingDefect(onRejected, None)(
            authenticateJwt(jwtToken).tapError(_ => onRejected(BaseEndpoints.Rejection.Unauthenticated)),
          ).flatMap { user =>
            attributingDefect(onRejected, Some(user))(
              authorization
                .ensureSystemAdmin(user)
                .tapError(_ => onRejected(BaseEndpoints.Rejection.NotSystemAdmin(user)))
                .as(user),
            )
          }
        case _ =>
          onRejected(BaseEndpoints.Rejection.Unauthenticated) *>
            ZIO.fail(BadCredentialsException("No credentials provided."))
      }

  /**
   * Attributes a defect thrown out of the security logic and turns it into a typed failure, so it is answered through
   * the endpoint's own error output instead of escaping to the server's outer exception handling.
   *
   * The status is a `500` either way -- both render the shared "Internal server error" body -- but the route the
   * response takes is not the same. A defect bypasses `onSecurityFailure`, and with it the interceptor that marks a
   * response produced before the request body was read as the last on its connection; on a streamed body that leaves
   * the connection unreadable and the client's next request on it hangs. Failing typed keeps the rejection inside the
   * hook that knows about that. The defect is logged here because it is a genuine bug and must not become quieter for
   * having been converted.
   *
   * `onRejected` runs here as well, and that is the point of splitting this per stage rather than wrapping the whole
   * chain. `tapError` does not fire for a `Cause.Die`, so a defect used to answer `500` with no entry at all on a
   * surface whose entire purpose is attributing every call -- and the wrapping conversion then hid which stage it
   * came from. Wrapping each stage keeps the identity a defect in the authorization check still has.
   */
  private def attributingDefect[A](onRejected: BaseEndpoints.Rejection => UIO[Unit], user: Option[User])(
    zio: IO[Throwable, A],
  ): IO[Throwable, A] =
    zio.foldCauseZIO(
      // The original `Cause` is logged rather than a `Cause.die` rebuilt from the throwable: the rebuilt one carries
      // the ZIO fiber trace of this line instead of the one where the defect was actually raised, which is the only
      // part of the report that says where to look.
      //
      // The predicate is shape-precise rather than a bare `cause.dieOption`, which is tree-wide (`find { case Die =>
      // }`). On a composite `Cause.Then(Fail(e), Die(t))` -- a finalizer dying alongside a typed rejection -- the
      // bare lookup matched, so `tapError` fired *and* this branch fired: two entries for one call, and the typed
      // `401`/`403` swallowed into an `AssertionException` and answered as a `500`. A typed failure wins whenever the
      // cause carries one; only a cause with a defect and no failure at all is a defect here. An interrupt-only or
      // empty cause takes neither branch, which is the documented "an interrupted call produces no entry".
      cause =>
        (if (cause.failureOption.isEmpty) cause.dieOption else None) match {
          case None         => ZIO.refailCause(cause)
          case Some(defect) =>
            ZIO.logErrorCause("Defect in the security logic", cause) *>
              onRejected(BaseEndpoints.Rejection.Defect(user)) *>
              ZIO.fail(AssertionException("Defect in the security logic", defect))
        },
      ZIO.succeed(_),
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

  /**
   * Why [[BaseEndpoints.bearerSystemAdminEndpoint]] turned a caller away, for a surface that has to attribute the
   * attempt. Deliberately not an outcome vocabulary: the naming and the log shape belong to the surface, this only
   * carries the distinctions the security logic is the last place to know.
   */
  enum Rejection {

    /** No credentials, or credentials that did not authenticate. No identity to attribute. */
    case Unauthenticated

    /** Authenticated, but not a SystemAdmin -- the one rejection that still knows who was refused. */
    case NotSystemAdmin(user: User)

    /** A bug in the security logic itself, answered as a `500`. The user is known if authentication had passed. */
    case Defect(user: Option[User])
  }

  private[api] final case class ErrorResponse(message: String)
  private[api] object ErrorResponse {
    implicit val codec: JsonCodec[ErrorResponse] = DeriveJsonCodec.gen[ErrorResponse]
  }
}
