/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.resources

import sttp.tapir.ztapir.*
import zio.*

class ResourcesServerEndpointsV3(
  resourcesEndpoints: ResourcesEndpointsV3,
  resourcesRestService: ResourcesRestServiceV3,
) {

  val endpoints: List[ZServerEndpoint[Any, Any]] = List(
    resourcesEndpoints.getResourcesResourcesPerOntology.zServerLogic(resourcesRestService.resourcesPerOntology),
    resourcesEndpoints.getResources.serverLogic(resourcesRestService.getResources),
  )
}

object ResourcesServerEndpointsV3 {
  val layer = ZLayer.derive[ResourcesServerEndpointsV3]
}
