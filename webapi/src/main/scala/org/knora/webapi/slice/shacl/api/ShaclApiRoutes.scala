/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.api

import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

final case class ShaclApiRoutes(shaclEndpointsHandler: ShaclEndpointsHandler, tapirToPekko: TapirToPekkoInterpreter) {
  private val handlers   = shaclEndpointsHandler.allHandlers
  val routes: Seq[Route] = handlers.map(tapirToPekko.toRoute(_))
}

object ShaclApiRoutes {
  val layer = ZLayer.derive[ShaclApiRoutes]
}
