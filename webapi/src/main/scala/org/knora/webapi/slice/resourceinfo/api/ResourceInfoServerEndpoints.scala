/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.resourceinfo.api.model.ListResponseDto
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.Asc
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.LastModificationDate
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.Order
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.OrderBy
import org.knora.webapi.slice.resourceinfo.api.service.RestResourceInfoService
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*

final case class ResourceInfoServerEndpoints(
  private val endpoints: ResourceInfoEndpoints,
  private val resourceInfoService: RestResourceInfoService,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    endpoints.getResourcesInfo.zServerLogic {
      case (projectIri: ProjectIri, resourceClass: String, order: Option[Order], orderBy: Option[OrderBy]) =>
        resourceInfoService.findByProjectAndResourceClass(
          projectIri,
          resourceClass,
          order.getOrElse(Asc),
          orderBy.getOrElse(LastModificationDate),
        )
    },
  )
}
object ResourceInfoServerEndpoints {
  val layer = ZLayer.derive[ResourceInfoServerEndpoints]
}
