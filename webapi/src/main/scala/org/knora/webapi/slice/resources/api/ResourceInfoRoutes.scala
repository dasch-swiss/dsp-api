/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.resources.api.model.ListResponseDto
import org.knora.webapi.slice.resources.api.service.ResourceInfoRestService

final case class ResourceInfoRoutes(
  endpoints: ResourceInfoEndpoints,
  resourceInfoService: ResourceInfoRestService,
  mapper: HandlerMapper,
  interpreter: TapirToPekkoInterpreter,
) {

  val routes: Seq[Route] =
    List(PublicEndpointHandler(endpoints.getResourcesInfo, resourceInfoService.findByProjectAndResourceClass))
      .map(mapper.mapPublicEndpointHandler)
      .map(interpreter.toRoute(_))
}
object ResourceInfoRoutes {
  val layer = ZLayer.derive[ResourceInfoRoutes]
}
