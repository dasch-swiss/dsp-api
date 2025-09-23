/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZIO
import zio.ZLayer
import zio.json.ast.Json

import org.knora.webapi.slice.admin.api.service.MaintenanceRestService
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

final case class MaintenanceEndpointsHandlers(
  endpoints: MaintenanceEndpoints,
  restService: MaintenanceRestService,
  mapper: HandlerMapper,
) {

  val allHandlers = List(
    SecuredEndpointHandler(endpoints.postMaintenance, restService.executeMaintenanceAction),
  ).map(mapper.mapSecuredEndpointHandler)
}

object MaintenanceEndpointsHandlers {
  val layer = ZLayer.derive[MaintenanceEndpointsHandlers]
}
