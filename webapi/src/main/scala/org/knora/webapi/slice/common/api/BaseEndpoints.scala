/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import dsp.errors.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM}
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.AnonymousUser
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.routing.{Authenticator, UnsafeZioRun}
import sttp.model.StatusCode
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.model.UsernamePassword
import sttp.tapir.{Endpoint, EndpointOutput, auth, cookie, endpoint, oneOf, oneOfVariant, statusCode}
import zio.{ZIO, ZLayer}

import scala.concurrent.Future

final case class BaseEndpoints(authenticator: Authenticator, implicit val r: zio.Runtime[Any]) {

  private val defaultErrorOutputs: EndpointOutput.OneOf[RequestRejectedException, RequestRejectedException] =
    oneOf[RequestRejectedException](
      oneOfVariant[NotFoundException](statusCode(StatusCode.NotFound).and(jsonBody[NotFoundException])),
      oneOfVariant[BadRequestException](statusCode(StatusCode.BadRequest).and(jsonBody[BadRequestException])),
      oneOfVariant[ValidationException](statusCode(StatusCode.BadRequest).and(jsonBody[ValidationException])),
      oneOfVariant[DuplicateValueException](statusCode(StatusCode.BadRequest).and(jsonBody[DuplicateValueException]))
    )

  private val secureDefaultErrorOutputs: EndpointOutput.OneOf[RequestRejectedException, RequestRejectedException] =
    oneOf[RequestRejectedException](
      // default
      oneOfVariant[NotFoundException](statusCode(StatusCode.NotFound).and(jsonBody[NotFoundException])),
      oneOfVariant[BadRequestException](statusCode(StatusCode.BadRequest).and(jsonBody[BadRequestException])),
      oneOfVariant[ValidationException](statusCode(StatusCode.BadRequest).and(jsonBody[ValidationException])),
      oneOfVariant[DuplicateValueException](statusCode(StatusCode.BadRequest).and(jsonBody[DuplicateValueException])),
      // plus security
      oneOfVariant[BadCredentialsException](statusCode(StatusCode.Unauthorized).and(jsonBody[BadCredentialsException])),
      oneOfVariant[ForbiddenException](statusCode(StatusCode.Forbidden).and(jsonBody[ForbiddenException]))
    )

  val publicEndpoint = endpoint.errorOut(defaultErrorOutputs)

  private val endpointWithBearerCookieBasicAuthOptional
    : Endpoint[(Option[String], Option[String], Option[UsernamePassword]), Unit, RequestRejectedException, Unit, Any] =
    endpoint
      .errorOut(secureDefaultErrorOutputs)
      .securityIn(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
      .securityIn(cookie[Option[String]](authenticator.calculateCookieName()))
      .securityIn(auth.basic[Option[UsernamePassword]](WWWAuthenticateChallenge.basic("realm")))

  val securedEndpoint = endpointWithBearerCookieBasicAuthOptional.serverSecurityLogic {
    case (Some(jwtToken), _, _) => authenticateJwt(jwtToken)
    case (_, Some(cookie), _)   => authenticateJwt(cookie)
    case (_, _, Some(basic))    => authenticateBasic(basic)
    case _                      => Future.successful(Left(BadCredentialsException("No credentials provided.")))
  }

  val withUserEndpoint = endpointWithBearerCookieBasicAuthOptional.serverSecurityLogic {
    case (Some(jwtToken), _, _) => authenticateJwt(jwtToken)
    case (_, Some(cookie), _)   => authenticateJwt(cookie)
    case (_, _, Some(basic))    => authenticateBasic(basic)
    case _                      => Future.successful(Right(AnonymousUser))
  }

  private def authenticateJwt(jwtToken: String): Future[Either[RequestRejectedException, UserADM]] =
    UnsafeZioRun.runToFuture(
      authenticator.verifyJwt(jwtToken).refineOrDie { case e: RequestRejectedException => e }.either
    )

  private def authenticateBasic(basic: UsernamePassword): Future[Either[RequestRejectedException, UserADM]] =
    UnsafeZioRun.runToFuture(
      ZIO
        .attempt(UserIdentifierADM(maybeEmail = Some(basic.username))(StringFormatter.getGeneralInstance))
        .map(id => Some(KnoraPasswordCredentialsV2(id, basic.password.getOrElse(""))))
        .flatMap(authenticator.getUserADMThroughCredentialsV2)
        .orElseFail(BadCredentialsException("Invalid credentials."))
        .refineOrDie { case e: RequestRejectedException => e }
        .either
    )
}

object BaseEndpoints {
  val layer = ZLayer.fromZIO(
    for {
      auth <- ZIO.service[Authenticator]
      r    <- ZIO.runtime[Any]
    } yield BaseEndpoints(auth, r)
  )
}
