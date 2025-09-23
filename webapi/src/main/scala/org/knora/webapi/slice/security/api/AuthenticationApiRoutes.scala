/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security.api

import zio.ZLayer

import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

final class AuthenticationApiRoutes(
  private val handler: AuthenticationEndpointsV2Handler,
  private val tapirToPekko: TapirToPekkoInterpreter,
) {
  val routes = handler.allHandlers.map(tapirToPekko.toRoute)
}

object AuthenticationApiRoutes {
  val layer = ZLayer.derive[AuthenticationApiRoutes]
}
