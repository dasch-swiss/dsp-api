/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import sttp.tapir.ztapir.*
import zio.*

final class ResourcesApiServerEndpoints(
  resourcesServerEndpoints: ResourcesServerEndpoints,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] =
    resourcesServerEndpoints.serverEndpoints
}
object ResourcesApiServerEndpoints {
  val layer = ZLayer.derive[ResourcesApiServerEndpoints]
}
