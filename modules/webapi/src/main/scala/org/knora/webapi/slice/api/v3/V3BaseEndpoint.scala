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
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.ProjectCandidate
import org.knora.webapi.slice.common.api.RequiresMethod
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.AuthenticatorError.*

final case class V3BaseEndpoint(
  private val authenticator: Authenticator,
  private val authorization: AuthorizationRestService,
) {

  private val policies = BaseEndpoints(authenticator, authorization)

  private type ErrorOut = EndpointOutput.OneOf[V3ErrorInfo, V3ErrorInfo]
  private val unauthorizedVariant = oneOfVariant(
    statusCode(StatusCode.Unauthorized).and(jsonBody[Unauthorized].example(Unauthorized("Invalid token."))),
  )
  private val forbiddenVariant = oneOfVariant(
    statusCode(StatusCode.Forbidden).and(jsonBody[Forbidden].example(Forbidden("User not active."))),
  )

  def public(errorOut: ErrorOut): RequiresMethod[PublicEndpoint[Unit, V3ErrorInfo, Unit, Any]] =
    RequiresMethod(endpoint.errorOut(errorOut), _.method(_))

  def secured(
    errorOut: ErrorOut,
  ): RequiresMethod[ZPartialServerEndpoint[Any, String, User, Unit, V3ErrorInfo, Unit, Any]] =
    RequiresMethod(
      endpoint
        .errorOut(errorOut)
        .errorOutVariantsPrepend(unauthorizedVariant, forbiddenVariant)
        .securityIn(auth.bearer[String](WWWAuthenticateChallenge.bearer))
        .zServerSecurityLogic(handleBearerJwt),
      _.method(_),
    )

  def withUser(
    errorOut: ErrorOut,
  ): RequiresMethod[ZPartialServerEndpoint[Any, Option[String], User, Unit, V3ErrorInfo, Unit, Any]] =
    RequiresMethod(
      endpoint
        .errorOut(errorOut)
        .errorOutVariantsPrepend(unauthorizedVariant, forbiddenVariant)
        .securityIn(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
        .zServerSecurityLogic {
          case Some(jwt) => handleBearerJwt(jwt)
          case _         => ZIO.succeed(AnonymousUser)
        },
      _.method(_),
    )

  private def withAuthErrors(errorOut: ErrorOut): PublicEndpoint[Unit, V3ErrorInfo, Unit, Any] =
    endpoint.errorOut(errorOut).errorOutVariantsPrepend(unauthorizedVariant, forbiddenVariant)

  private def forbidden(e: dsp.errors.ForbiddenException): V3ErrorInfo = Forbidden(e.message)

  /** Declares and enforces SystemAdmin in the security logic; see [[BaseEndpoints.bearerSystemAdminPolicyEndpoint]]. */
  def systemAdminPolicy(
    errorOut: ErrorOut,
  ): RequiresMethod[ZPartialServerEndpoint[Any, String, User, Unit, V3ErrorInfo, Unit, Any]] =
    policies.bearerSystemAdminPolicyEndpoint(withAuthErrors(errorOut), handleBearerJwt, forbidden)

  /** Declares ObjectAccess for a read; anonymous callers are admitted. */
  def objectAccessPolicy(
    errorOut: ErrorOut,
    enforcedIn: String,
  ): RequiresMethod[ZPartialServerEndpoint[Any, Option[String], User, Unit, V3ErrorInfo, Unit, Any]] =
    policies.bearerObjectAccessPolicyEndpoint(withAuthErrors(errorOut), enforcedIn, handleBearerJwt)

  /** Declares ProjectAdmin; the handler must obtain a grant from [[V3Authorizer.authorizeProjectAdminById]]. */
  def projectAdminPolicy(
    errorOut: ErrorOut,
    projectFrom: String,
  ): RequiresMethod[ZPartialServerEndpoint[Any, String, ProjectCandidate, Unit, V3ErrorInfo, Unit, Any]] =
    policies.bearerProjectAdminPolicyEndpoint(withAuthErrors(errorOut), projectFrom, handleBearerJwt, forbidden)

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
