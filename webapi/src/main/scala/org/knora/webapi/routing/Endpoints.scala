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
import org.knora.webapi.slice.api.v3.ApiV3ServerEndpoints
import org.knora.webapi.slice.infrastructure.api.ManagementServerEndpoints
import org.knora.webapi.slice.shacl.api.ShaclServerEndpoints

final case class Endpoints(
  private val adminApi: AdminApiServerEndpoints, // admin api
  private val apiV2: ApiV2ServerEndpoints,
  private val apiV3: ApiV3ServerEndpoints,
  private val shacl: ShaclServerEndpoints,
  private val management: ManagementServerEndpoints,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] =
    adminApi.serverEndpoints ++
      apiV2.serverEndpoints ++
      apiV3.serverEndpoints ++
      management.serverEndpoints ++
      shacl.serverEndpoints
}
object Endpoints {
  val layer = ZLayer.derive[Endpoints]
}
