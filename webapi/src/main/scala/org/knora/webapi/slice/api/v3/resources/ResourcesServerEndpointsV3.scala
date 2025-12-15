/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.resources

import sttp.tapir.ztapir.*
import zio.*

class ResourcesServerEndpointsV3(endpoints: ResourcesEndpointsV3, restService: ResourcesRestServiceV3) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getResources.serverLogic(restService.getResources),
    endpoints.getResourcesResourcesPerOntology.zServerLogic(restService.resourcesPerOntology),
  )
}

object ResourcesServerEndpointsV3 {
  val layer = ZLayer.derive[ResourcesServerEndpointsV3]
}
