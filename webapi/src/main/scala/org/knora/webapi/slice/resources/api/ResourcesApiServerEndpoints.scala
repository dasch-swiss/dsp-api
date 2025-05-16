/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*
import zio.ZLayer

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
