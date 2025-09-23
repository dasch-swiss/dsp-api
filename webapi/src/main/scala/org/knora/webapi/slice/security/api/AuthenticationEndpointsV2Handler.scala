/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security.api
import sttp.model.headers.CookieValueWithMeta
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.SecuredEndpointHandler
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.CheckResponse
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginForm
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LogoutResponse
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.TokenResponse

case class AuthenticationEndpointsV2Handler(
  appConfig: AppConfig,
  restService: AuthenticationRestService,
  endpoints: AuthenticationEndpointsV2,
  mapper: HandlerMapper,
) {
  val allHandlers = List(
    SecuredEndpointHandler(endpoints.getV2Authentication, _ => _ => ZIO.succeed(CheckResponse("credentials are OK"))),
  ).map(mapper.mapSecuredEndpointHandler) ++ List(
    PublicEndpointHandler(endpoints.postV2Authentication, restService.authenticate),
    PublicEndpointHandler(endpoints.deleteV2Authentication, restService.logout),
    PublicEndpointHandler(endpoints.getV2Login, restService.loginForm),
    PublicEndpointHandler(endpoints.postV2Login, restService.authenticate),
  ).map(mapper.mapPublicEndpointHandler)
}

object AuthenticationEndpointsV2Handler {
  val layer = ZLayer.derive[AuthenticationEndpointsV2Handler]
}
