/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import sttp.model.StatusCode
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.EndpointOutput
import sttp.tapir.PublicEndpoint
import sttp.tapir.Validator
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.ztapir.*
import zio.*
import zio.json.JsonEncoder

import org.knora.webapi.messages.util.KnoraSystemInstances.Users.AnonymousUser
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.AuthenticatorError.*

final case class V3BaseEndpoint(private val authenticator: Authenticator) {
  private val defaultErrorOut: EndpointOutput.OneOf[V3ErrorInfo, V3ErrorInfo] =
    oneOf[V3ErrorInfo](
      // default
      oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[NotFound])),
      oneOfVariant(statusCode(StatusCode.BadRequest).and(jsonBody[BadRequest])),
    )

  private val secureErrorOut = oneOf[V3ErrorInfo](
    // default
    oneOfVariant(statusCode(StatusCode.NotFound).and(jsonBody[NotFound])),
    oneOfVariant(statusCode(StatusCode.BadRequest).and(jsonBody[BadRequest])),
    // plus security
    oneOfVariant(statusCode(StatusCode.Unauthorized).and(jsonBody[Unauthorized])),
    oneOfVariant(statusCode(StatusCode.Forbidden).and(jsonBody[Forbidden])),
  )

  def publicWithErrorOut[O <: V3ErrorInfo](
    errorOut: EndpointOutput.OneOfVariant[O],
  ): PublicEndpoint[Unit, V3ErrorInfo, Unit, Any] =
    endpoint.errorOut(oneOf[V3ErrorInfo](errorOut))

  val publicEndpoint: PublicEndpoint[Unit, V3ErrorInfo, Unit, Any] = endpoint.errorOut(defaultErrorOut)

  private val endpointWithSecureErrorOut = endpoint.errorOut(secureErrorOut)

  val securedEndpoint: ZPartialServerEndpoint[Any, String, User, Unit, V3ErrorInfo, Unit, Any] =
    endpointWithSecureErrorOut
      .securityIn(auth.bearer[String](WWWAuthenticateChallenge.bearer))
      .zServerSecurityLogic(handleBearerJwt)

  val withUserEndpoint: ZPartialServerEndpoint[Any, Option[String], User, Unit, V3ErrorInfo, Unit, Any] =
    endpointWithSecureErrorOut
      .securityIn(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
      .zServerSecurityLogic {
        case Some(jwt) => handleBearerJwt(jwt)
        case _         => ZIO.succeed(AnonymousUser)
      }

  private def handleBearerJwt(jwt: String): IO[V3ErrorInfo, User] =
    authenticator.authenticate(jwt).mapError {
      case BadCredentials => Unauthorized("Invalid token.")
      case UserNotFound   => Forbidden("User not found.")
      case UserNotActive  => Forbidden("User not active.")
    }
}

object V3BaseEndpoint {
  val layer = ZLayer.derive[V3BaseEndpoint]

  type EndpointT = ZServerEndpoint[Any, Any]
}
