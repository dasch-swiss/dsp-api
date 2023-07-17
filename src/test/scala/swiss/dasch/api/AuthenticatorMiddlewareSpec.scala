/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.test.SpecConfigurations.jwtConfigLayer
import zio.*
import zio.http.*
import zio.http.endpoint.*
import zio.test.{ TestAspect, ZIOSpecDefault, assertTrue }

object AuthenticatorMiddlewareSpec extends ZIOSpecDefault {

  private val app =
    Endpoint.get("hello").out[String].implement(_ => ZIO.succeed("test")).toApp @@ Authenticator.middleware

  private val request = Request.get(URL(Root / "hello"))

  val spec = suite("AuthenticationMiddlewareSpec")(
    test("valid token should be accepted") {
      for {
        token    <- SpecJwtTokens.validToken()
        _        <- Authenticator.authenticate(token)
        response <- app.runZIO(request.updateHeaders(_.addHeader(Header.Authorization.Bearer(token))))
        body     <- response.body.asString
      } yield assertTrue(response.status == Status.Ok, body == "\"test\"")
    },
    test("request without auth header should be unauthorized") {
      for {
        response <- app.runZIO(request)
      } yield assertTrue(response.status == Status.Unauthorized, response.body == Body.empty)
    },
    test("request with invalid token should be unauthorized") {
      for {
        token    <- SpecJwtTokens.tokenWithInvalidSignature()
        response <- app.runZIO(request.updateHeaders(_.addHeader(Header.Authorization.Bearer(token))))
      } yield assertTrue(response.status == Status.Unauthorized, response.body == Body.empty)
    },
  ).provide(jwtConfigLayer, AuthenticatorLive.layer) @@ TestAspect.withLiveClock
}
