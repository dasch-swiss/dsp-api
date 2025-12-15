/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.authentication

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.CheckResponse
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginForm
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginPayload

final class AuthenticationServerEndpoints(
  restService: AuthenticationRestService,
  endpoints: AuthenticationEndpointsV2,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getV2Authentication.serverLogic(_ => _ => ZIO.succeed(CheckResponse("credentials are OK"))),
    endpoints.postV2Authentication.zServerLogic(restService.authenticate),
    endpoints.deleteV2Authentication.zServerLogic(restService.logout),
    endpoints.getV2Login.zServerLogic(restService.loginForm),
    endpoints.postV2Login.zServerLogic(restService.authenticate),
  )
}

object AuthenticationServerEndpoints {
  val layer =
    AuthenticationEndpointsV2.layer >+> AuthenticationRestService.layer >>> ZLayer.derive[AuthenticationServerEndpoints]
}
