/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import sttp.model.StatusCode
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.EndpointOutput
import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.common.api.ClosesConnectionOnEarlyRejection
import org.knora.webapi.slice.common.api.EarlyRejectionClosesConnection
import org.knora.webapi.slice.common.api.RequiresMethod

/**
 * The bases of every `/ingest/` endpoint. They accept a bearer token only (no basic, no cookie) and answer with
 * [[ApiProblem]] bodies, declared here rather than in the shared `BaseEndpoints.errorOutputs`.
 */
final class IngestBaseEndpoints(authenticator: IngestAuthenticator) {
  import IngestBaseEndpoints.errorOutputs

  private val base = endpoint
    .in("ingest")
    .errorOut(errorOutputs)
    // On every ingest route, not only the uploads: the server closes only when a declared body went unread.
    .attribute(ClosesConnectionOnEarlyRejection.key, EarlyRejectionClosesConnection)

  val publicEndpoint: RequiresMethod[PublicEndpoint[Unit, ApiProblem, Unit, Any]] =
    RequiresMethod(base, _.method(_))

  /** An absent or invalid token is anonymous (`None`), never a `401`. */
  val withPrincipalEndpoint: RequiresMethod[
    ZPartialServerEndpoint[Any, Option[String], Option[IngestPrincipal], Unit, ApiProblem, Unit, Any],
  ] =
    RequiresMethod(
      base
        .securityIn(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
        .zServerSecurityLogic[Any, Option[IngestPrincipal]](token =>
          ZIO.foreach(token)(authenticator.authenticate).catchAll(_ => ZIO.none),
        ),
      _.method(_),
    )

  val securedEndpoint
    : RequiresMethod[ZPartialServerEndpoint[Any, String, IngestPrincipal, Unit, ApiProblem, Unit, Any]] =
    RequiresMethod(
      base
        .securityIn(auth.bearer[String](WWWAuthenticateChallenge.bearer))
        .zServerSecurityLogic[Any, IngestPrincipal](token =>
          authenticator
            .authenticate(token)
            .mapError(e => ApiProblem.Unauthorized(e.map(_.message).mkString(", "))),
        ),
      _.method(_),
    )
}

object IngestBaseEndpoints {

  val errorOutputs: EndpointOutput.OneOf[ApiProblem, ApiProblem] =
    oneOf[ApiProblem](
      oneOfVariant(statusCode(StatusCode.BadRequest).and(jsonBody[ApiProblem.BadRequest])),
      oneOfVariant(statusCode(StatusCode.InternalServerError).and(jsonBody[ApiProblem.InternalServerError])),
      oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[ApiProblem.NotFound])),
      oneOfVariant(statusCode(StatusCode.ServiceUnavailable).and(jsonBody[ApiProblem.Unhealthy])),
      oneOfVariant(statusCode(StatusCode.Conflict).and(jsonBody[ApiProblem.Conflict])),
      oneOfVariant(statusCode(StatusCode.Unauthorized).and(jsonBody[ApiProblem.Unauthorized])),
      oneOfVariant(statusCode(StatusCode.Forbidden).and(jsonBody[ApiProblem.Forbidden])),
    )

  val layer: URLayer[IngestAuthenticator, IngestBaseEndpoints] = ZLayer.derive[IngestBaseEndpoints]
}
