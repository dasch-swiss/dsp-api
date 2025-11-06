/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.resources
import sttp.tapir.ztapir.ZServerEndpoint
import zio.*

final case class ResourcesServerEndpoints(
  private val resourcesEndpoints: ResourcesEndpoints,
  private val resourcesRestService: ResourcesRestServiceV3,
) {

  val endpoints: List[ZServerEndpoint[Any, Any]] = List(
    resourcesEndpoints.getResourcesResourcesPerOntology.serverLogic(resourcesRestService.resourcesPerOntology),
  )
}

object ResourcesServerEndpoints {
  val layer = ZLayer.derive[ResourcesServerEndpoints]
}
