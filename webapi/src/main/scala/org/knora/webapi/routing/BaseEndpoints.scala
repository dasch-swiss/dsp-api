/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import sttp.model.StatusCode
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.{EndpointOutput, auth, cookie, endpoint, oneOf, oneOfVariant, statusCode}
import sttp.tapir.generic.auto._
import sttp.tapir.json.zio.jsonBody
import zio.ZIO
import zio.ZLayer

import scala.concurrent.Future
import dsp.errors.BadCredentialsException
import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import dsp.errors.RequestRejectedException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM}
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import sttp.tapir.model.UsernamePassword

final case class BaseEndpoints(authenticator: Authenticator, implicit val r: zio.Runtime[Any]) {

  private val defaultErrorOutputs: EndpointOutput.OneOf[RequestRejectedException, RequestRejectedException] =
    oneOf[RequestRejectedException](
      oneOfVariant[NotFoundException](statusCode(StatusCode.NotFound).and(jsonBody[NotFoundException])),
      oneOfVariant[BadRequestException](statusCode(StatusCode.BadRequest).and(jsonBody[BadRequestException]))
    )

  private val secureDefaultErrorOutputs: EndpointOutput.OneOf[RequestRejectedException, RequestRejectedException] =
    oneOf[RequestRejectedException](
      oneOfVariant[NotFoundException](statusCode(StatusCode.NotFound).and(jsonBody[NotFoundException])),
      oneOfVariant[BadRequestException](statusCode(StatusCode.BadRequest).and(jsonBody[BadRequestException])),
      oneOfVariant[BadCredentialsException](statusCode(StatusCode.Unauthorized).and(jsonBody[BadCredentialsException])),
      oneOfVariant[ForbiddenException](statusCode(StatusCode.Forbidden).and(jsonBody[ForbiddenException]))
    )

  val publicEndpoint = endpoint.errorOut(defaultErrorOutputs)

  val securedEndpoint = endpoint
    .errorOut(secureDefaultErrorOutputs)
    .securityIn(auth.bearer[Option[String]](WWWAuthenticateChallenge.bearer))
    .securityIn(cookie[Option[String]](authenticator.calculateCookieName()))
    .securityIn(auth.basic[Option[UsernamePassword]](WWWAuthenticateChallenge.basic("realm")))
    .serverSecurityLogic {
      case (Some(jwtToken), _, _) => authenticateJwt(jwtToken)
      case (_, Some(cookie), _)   => authenticateJwt(cookie)
      case (_, _, Some(basic))    => authenticateBasic(basic)
      case _                      => Future.successful(Left(BadCredentialsException("No credentials provided.")))
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
