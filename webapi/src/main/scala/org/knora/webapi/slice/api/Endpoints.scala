/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.admin.api.AdminApiServerEndpoints
import org.knora.webapi.slice.api.management.ManagementServerEndpoints
import org.knora.webapi.slice.api.v2.ApiV2ServerEndpoints
import org.knora.webapi.slice.api.v3.ApiV3ServerEndpoints
import org.knora.webapi.slice.shacl.api.ShaclServerEndpoints

final class Endpoints(
  adminApi: AdminApiServerEndpoints,
  apiV2: ApiV2ServerEndpoints,
  apiV3: ApiV3ServerEndpoints,
  shacl: ShaclServerEndpoints,
  management: ManagementServerEndpoints,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] =
    adminApi.serverEndpoints ++
      apiV2.serverEndpoints ++
      apiV3.serverEndpoints ++
      management.serverEndpoints ++
      shacl.serverEndpoints
}

object Endpoints {
  private[api] val layer = ZLayer.derive[Endpoints]
}
