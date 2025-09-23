/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.*
import zio.json.ast.Json

import sttp.tapir.ztapir.*

import org.knora.webapi.slice.admin.api.service.MaintenanceRestService
import org.knora.webapi.slice.admin.domain.model.User

final case class MaintenanceEndpointsHandlers(
  endpoints: MaintenanceEndpoints,
  restService: MaintenanceRestService,
) {
  val allHandlers: ZServerEndpoint[Any, Any] = Seq(
    endpoints.postMaintenance.serverLogic(restService.executeMaintenanceAction),
  )
}
object MaintenanceEndpointsHandlers {
  val layer = ZLayer.derive[MaintenanceEndpointsHandlers]
}
