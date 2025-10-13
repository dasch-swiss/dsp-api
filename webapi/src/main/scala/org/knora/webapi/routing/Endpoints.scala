/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.routing
import org.knora.webapi.slice.admin.api.AdminApiServerEndpoints
import org.knora.webapi.slice.api.v2.ApiV2ServerEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementServerEndpoints
import org.knora.webapi.slice.shacl.api.ShaclServerEndpoints

final case class Endpoints(
  adminApiServerEndpoints: AdminApiServerEndpoints, // admin api
  apiV2ServerEndpoints: ApiV2ServerEndpoints,
  shaclServerEndpoints: ShaclServerEndpoints,
  managementServerEndpoints: ManagementServerEndpoints,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] =
    adminApiServerEndpoints.serverEndpoints ++
      apiV2ServerEndpoints.serverEndpoints ++
      managementServerEndpoints.serverEndpoints ++
      shaclServerEndpoints.serverEndpoints
}
object Endpoints {
  val layer = ZLayer.derive[Endpoints]
}
