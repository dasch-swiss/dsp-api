/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.admin.service.MaintenanceRestService

final class MaintenanceServerEndpoints(
  endpoints: MaintenanceEndpoints,
  restService: MaintenanceRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.postMaintenance.serverLogic(restService.executeMaintenanceAction),
  )
}
object MaintenanceServerEndpoints {
  val layer = ZLayer.derive[MaintenanceServerEndpoints]
}
