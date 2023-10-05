/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import sttp.model.StatusCode
import sttp.model.headers.WWWAuthenticateChallenge
import sttp.tapir.EndpointOutput
import sttp.tapir.auth
import sttp.tapir.endpoint
import sttp.tapir.generic.auto._
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.oneOf
import sttp.tapir.oneOfVariant
import sttp.tapir.statusCode
import zio.ZIO
import zio.ZLayer

import scala.concurrent.Future

import dsp.errors.BadCredentialsException
import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import dsp.errors.RequestRejectedException
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

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
    .securityIn(auth.bearer[String](WWWAuthenticateChallenge.bearer))
    .serverSecurityLogic(authenticateJwt)

  private def authenticateJwt(jwtToken: String): Future[Either[RequestRejectedException, UserADM]] =
    UnsafeZioRun.runToFuture(
      authenticator.verifyJwt(jwtToken).refineOrDie { case e: RequestRejectedException => e }.either
    )

  private def authenticateBasic(basic: String): Future[Either[RequestRejectedException, UserADM]] =
    UnsafeZioRun.runToFuture(
      ZIO.logError(s"Basic authentication $basic is not supported yet.") *> ZIO.die(new UnsupportedOperationException())
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
