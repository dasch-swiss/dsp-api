package org.knora.webapi.slice.shacl.api

import zio.ZLayer

import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler

case class ShaclEndpointsHandler(
  shaclEndpoints: ShaclEndpoints,
  shaclApiService: ShaclApiService,
  mapper: HandlerMapper,
) {

  val validate = PublicEndpointHandler(shaclEndpoints.validate, shaclApiService.validate)

  val allHandlers = List(validate).map(mapper.mapPublicEndpointHandler)
}

object ShaclEndpointsHandler {
  val layer = ZLayer.derive[ShaclEndpointsHandler]
}
