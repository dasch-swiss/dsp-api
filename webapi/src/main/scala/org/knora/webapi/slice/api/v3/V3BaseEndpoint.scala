/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import sttp.capabilities.zio.ZioStreams
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

  private type ErrorOut = EndpointOutput.OneOf[V3ErrorInfo, V3ErrorInfo]
  private val unauthorizedVariant = oneOfVariant(
    statusCode(StatusCode.Unauthorized).and(jsonBody[Unauthorized].example(Unauthorized("Invalid token."))),
  )
  private val forbiddenVariant = oneOfVariant(
    statusCode(StatusCode.Forbidden).and(jsonBody[Forbidden].example(Forbidden("User not active."))),
  )

  def public(errorOut: ErrorOut): PublicEndpoint[Unit, V3ErrorInfo, Unit, Any] =
    endpoint.errorOut(errorOut)

  def secured(errorOut: ErrorOut): ZPartialServerEndpoint[Any, String, User, Unit, V3ErrorInfo, Unit, Any] =
    endpoint
      .errorOut(errorOut)
      .errorOutVariantsPrepend(unauthorizedVariant, forbiddenVariant)
      .securityIn(auth.bearer[String](WWWAuthenticateChallenge.bearer))
      .zServerSecurityLogic(handleBearerJwt)

  def withUser(errorOut: ErrorOut): ZPartialServerEndpoint[Any, Option[String], User, Unit, V3ErrorInfo, Unit, Any] =
    endpoint
      .errorOut(errorOut)
      .errorOutVariantsPrepend(unauthorizedVariant, forbiddenVariant)
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

  type EndpointT = ZServerEndpoint[Any, ZioStreams]
}
