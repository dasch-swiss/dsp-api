/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3
import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.api.v3.`export`.ExportServerEndpoints
import org.knora.webapi.slice.api.v3.resources.ResourcesServerEndpointsV3
import org.knora.webapi.slice.api.v3.projects.*

final class ApiV3ServerEndpoints(
  resourcesServerEndpoints: ResourcesServerEndpointsV3,
  exportServerEndpoints: ExportServerEndpoints,
  projectServerEndpoints: V3ProjectsServerEndpoints,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] =
    (resourcesServerEndpoints.serverEndpoints ++
      exportServerEndpoints.serverEndpoints ++
      projectServerEndpoints.serverEndpoints)
      .map(_.tag("API v3"))
}
object ApiV3ServerEndpoints {
  val layer = ZLayer.derive[ApiV3ServerEndpoints]
}
