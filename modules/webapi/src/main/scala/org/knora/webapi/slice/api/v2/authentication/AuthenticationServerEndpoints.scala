/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.authentication

import sttp.tapir.ztapir.*
import zio.*

final class AuthenticationServerEndpoints(
  restService: AuthenticationRestService,
  endpoints: AuthenticationEndpointsV2,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getV2Authentication.zServerLogic(restService.checkAuthentication),
    endpoints.postV2Authentication.zServerLogic(restService.authenticate),
    endpoints.deleteV2Authentication.zServerLogic(restService.logout),
  )
}

object AuthenticationServerEndpoints {
  val layer =
    AuthenticationEndpointsV2.layer >+> AuthenticationRestService.layer >>> ZLayer.derive[AuthenticationServerEndpoints]
}
