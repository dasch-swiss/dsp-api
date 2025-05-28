/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api
import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer

import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

final case class ResourcesApiRoutes(
  private val metadataEndpoints: MetadataServerEndpoints,
  private val resourcesEndpoints: ResourcesEndpointsHandler,
  private val standoffEndpoints: StandoffEndpointsHandler,
  private val valuesEndpoints: ValuesEndpointsHandler,
  private val tapirToPekko: TapirToPekkoInterpreter,
) {

  private val handlers =
    valuesEndpoints.allHandlers ++ resourcesEndpoints.allHandlers ++ standoffEndpoints.allHandlers ++ metadataEndpoints.allHandlers

  val routes: Seq[Route] = handlers.map(tapirToPekko.toRoute(_))
}

object ResourcesApiRoutes {
  val layer = ZLayer.derive[ResourcesApiRoutes]
}
