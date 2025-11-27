/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.`export`.api.ExportServerEndpoints
import org.knora.webapi.slice.api.v3.resources.ResourcesServerEndpointsV3

class ApiV3ServerEndpoints(
  private val resourcesServerEndpoints: ResourcesServerEndpointsV3,
  private val exportServerEndpoints: ExportServerEndpoints,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] =
    (resourcesServerEndpoints.endpoints ++ exportServerEndpoints.endpoints)
      .map(_.tag("API v3"))
}
object ApiV3ServerEndpoints {
  val layer = ZLayer.derive[ApiV3ServerEndpoints]
}
