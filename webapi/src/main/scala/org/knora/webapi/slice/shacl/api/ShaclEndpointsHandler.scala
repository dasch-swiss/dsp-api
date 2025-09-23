/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.api

import zio.ZLayer

import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler

case class ShaclEndpointsHandler(
  private val shaclEndpoints: ShaclEndpoints,
  private val shaclApiService: ShaclApiService,
  private val mapper: HandlerMapper,
) {

  val allHandlers =
    List(PublicEndpointHandler(shaclEndpoints.validate, shaclApiService.validate)).map(mapper.mapPublicEndpointHandler)
}

object ShaclEndpointsHandler {
  val layer = ZLayer.derive[ShaclEndpointsHandler]
}
