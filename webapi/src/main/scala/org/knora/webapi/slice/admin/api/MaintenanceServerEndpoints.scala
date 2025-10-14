/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.ztapir.*
import zio.*
import zio.json.ast.Json

import org.knora.webapi.slice.admin.api.service.MaintenanceRestService
import org.knora.webapi.slice.admin.domain.model.User

final case class MaintenanceServerEndpoints(
  private val endpoints: MaintenanceEndpoints,
  private val restService: MaintenanceRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.postMaintenance.serverLogic(restService.executeMaintenanceAction),
  )
}
object MaintenanceServerEndpoints {
  val layer = ZLayer.derive[MaintenanceServerEndpoints]
}
