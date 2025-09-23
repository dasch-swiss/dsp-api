/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security.api

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.security.Authenticator

object AuthenticationApiModule { self =>
  type Dependencies = AppConfig & Authenticator & BaseEndpoints & HandlerMapper & TapirToPekkoInterpreter
  type Provided     = AuthenticationApiRoutes & AuthenticationEndpointsV2
  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      AuthenticationEndpointsV2.layer,
      AuthenticationEndpointsV2Handler.layer,
      AuthenticationApiRoutes.layer,
      AuthenticationRestService.layer,
    )
}
