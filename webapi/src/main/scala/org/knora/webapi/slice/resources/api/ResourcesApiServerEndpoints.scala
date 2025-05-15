/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer
import sttp.tapir.ztapir.*
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import sttp.capabilities.zio.ZioStreams

final case class ResourcesApiServerEndpoints(
  private val valuesEndpoints: ValuesServerEndpoints,
  private val resourcesEndpoints: ResourcesServerEndpoints,
  private val standoffEndpoints: StandoffServerEndpoints,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] =
    valuesEndpoints.serverEndpoints ++
      resourcesEndpoints.serverEndpoints ++
      standoffEndpoints.serverEndpoints
}

object ResourcesApiServerEndpoints {
  val layer = ZLayer.derive[ResourcesApiServerEndpoints]
}
