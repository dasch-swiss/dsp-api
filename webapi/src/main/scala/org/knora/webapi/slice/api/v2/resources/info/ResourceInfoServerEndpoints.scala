/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.resources.info

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

final class ResourceInfoServerEndpoints(endpoints: ResourceInfoEndpoints, restService: ResourceInfoRestService) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getResourcesInfo.zServerLogic(restService.findByProjectAndResourceClass),
  )
}
object ResourceInfoServerEndpoints {
  val layer = ResourceInfoEndpoints.layer >+>
    ResourceInfoRestService.layer >>>
    ZLayer.derive[ResourceInfoServerEndpoints]
}
