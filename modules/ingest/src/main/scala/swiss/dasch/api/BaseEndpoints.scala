/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.model.StatusCode
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.Codec
import sttp.tapir.EndpointOutput
import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.ztapir.*
import swiss.dasch.api.ApiProblem.Unauthorized
import swiss.dasch.api.BaseEndpoints.defaultErrorOutputs
import zio.*

case class BaseEndpoints(authService: AuthService) {
  val publicEndpoint: RequiresMethod[PublicEndpoint[Unit, ApiProblem, Unit, Any]] =
    RequiresMethod(endpoint.errorOut(defaultErrorOutputs), _.method(_))

  val withUserEndpoint
    : RequiresMethod[ZPartialServerEndpoint[Any, Option[String], Option[Principal], Unit, ApiProblem, Unit, Any]] =
    RequiresMethod(
      endpoint
        .errorOut(defaultErrorOutputs)
        .securityIn(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
        .zServerSecurityLogic[Any, Option[Principal]](handleAuthOpt),
      _.method(_),
    )

  val secureEndpoint: RequiresMethod[ZPartialServerEndpoint[Any, String, Principal, Unit, ApiProblem, Unit, Any]] =
    RequiresMethod(
      endpoint
        .errorOut(defaultErrorOutputs)
        .securityIn(auth.bearer[String](WWWAuthenticateChallenge.bearer))
        .zServerSecurityLogic[Any, Principal](handleAuth),
      _.method(_),
    )

  private def handleAuthOpt(token: Option[String]): IO[Nothing, Option[Principal]] =
    ZIO.foreach(token)(authService.authenticate(_)).catchAll(_ => ZIO.succeed(None: Option[Principal]))

  private def handleAuth(token: String): IO[ApiProblem, Principal] =
    authService.authenticate(token).mapError(e => ApiProblem.Unauthorized(e.map(_.message).mkString(", ")))
}

object BaseEndpoints {

  val layer = ZLayer.derive[BaseEndpoints]

  val defaultErrorOutputs: EndpointOutput.OneOf[ApiProblem, ApiProblem] =
    oneOf[ApiProblem](
      oneOfVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiProblem.BadRequest])),
      oneOfVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[ApiProblem.InternalServerError])),
      oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[ApiProblem.NotFound])),
      oneOfVariant(statusCode(StatusCode.ServiceUnavailable).and(jsonBody[ApiProblem.Unhealthy])),
      oneOfVariant(statusCode(StatusCode.Conflict).and(jsonBody[ApiProblem.Conflict])),
      oneOfVariant(statusCode(StatusCode.Unauthorized).and(jsonBody[ApiProblem.Unauthorized])),
      oneOfVariant(statusCode(StatusCode.Forbidden).and(jsonBody[ApiProblem.Forbidden])),
    )
}
