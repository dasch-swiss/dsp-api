/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import zio.ZLayer

import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointHandler
import org.knora.webapi.slice.resources.api.service.StandoffRestService

final case class StandoffEndpointsHandler(
  endpoints: StandoffEndpoints,
  standoffRestService: StandoffRestService,
  mapper: HandlerMapper,
) {
  val allHandlers = Seq(SecuredEndpointHandler(endpoints.postMapping, standoffRestService.createMapping))
    .map(mapper.mapSecuredEndpointHandler)
}

object StandoffEndpointsHandler {
  val layer = ZLayer.derive[StandoffEndpointsHandler]
}
