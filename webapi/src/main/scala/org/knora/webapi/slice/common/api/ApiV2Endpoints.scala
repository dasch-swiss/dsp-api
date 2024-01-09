package org.knora.webapi.slice.common.api

import org.knora.webapi.slice.resourceinfo.api.ResourceInfoEndpoints
import org.knora.webapi.slice.search.api.SearchEndpoints
import sttp.tapir.AnyEndpoint
import zio.ZLayer

final case class ApiV2Endpoints(
  resourceInfoEndpoints: ResourceInfoEndpoints,
                                 searchEndpoints: SearchEndpoints) {

  val endpoints: Seq[AnyEndpoint] =
    resourceInfoEndpoints.endpoints ++
    searchEndpoints.endpoints
}

object ApiV2Endpoints {
  val layer = ZLayer.derive[ApiV2Endpoints]
}
