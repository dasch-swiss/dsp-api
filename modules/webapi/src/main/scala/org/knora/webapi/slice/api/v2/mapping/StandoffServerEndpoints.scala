/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.mapping

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.responders.v2.StandoffResponderV2
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

final class StandoffServerEndpoints(endpoints: StandoffEndpoints, restService: StandoffRestService) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.postMapping.serverLogic(restService.createMapping),
  )
}
object StandoffServerEndpoints {

  type Dependencies =
    AuthorizationRestService & BaseEndpoints & KnoraResponseRenderer & ApiComplexV2JsonLdRequestParser &
      StandoffResponderV2

  type Provided = StandoffServerEndpoints

  val layer: URLayer[Dependencies, Provided] = StandoffEndpoints.layer >+>
    StandoffRestService.layer >>>
    ZLayer.derive[StandoffServerEndpoints]
}
