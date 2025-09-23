/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security.api
import sttp.model.headers.CookieValueWithMeta
import zio.*
import sttp.tapir.ztapir.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.CheckResponse
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginForm
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LogoutResponse
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.TokenResponse

case class AuthenticationServerEndpoints(
  private val restService: AuthenticationRestService,
  private val endpoints: AuthenticationEndpointsV2,
) {
  val serverEndpoints = Seq(
    endpoints.getV2Authentication.serverLogic(_ => _ => ZIO.succeed(CheckResponse("credentials are OK"))),
    endpoints.postV2Authentication.zServerLogic(restService.authenticate),
    endpoints.deleteV2Authentication.zServerLogic(restService.logout),
    endpoints.getV2Login.zServerLogic(restService.loginForm),
    endpoints.postV2Login.zServerLogic(restService.authenticate),
  )
}

object AuthenticationServerEndpoints {
  val layer = ZLayer.derive[AuthenticationServerEndpoints]
}
