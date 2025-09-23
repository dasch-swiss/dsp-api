/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import zio.*
import sttp.tapir.ztapir.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.resources.api.model.ListResponseDto
import org.knora.webapi.slice.resources.api.service.ResourceInfoRestService

final case class ResourceInfoServerEndpoints(
  private val endpoints: ResourceInfoEndpoints,
  private val resourceInfoService: ResourceInfoRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getResourcesInfo.zServerLogic(resourceInfoService.findByProjectAndResourceClass),
  )
}
object ResourceInfoServerEndpoints {
  val layer = ZLayer.derive[ResourceInfoServerEndpoints]
}
