/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer

import org.knora.webapi.slice.common.api.EndpointAndZioHandler
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.resourceinfo.api.model.ListResponseDto
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.ASC
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.Order
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.OrderBy
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.lastModificationDate
import org.knora.webapi.slice.resourceinfo.api.service.RestResourceInfoService

final case class ResourceInfoRoutes(
  endpoints: ResourceInfoEndpoints,
  resourceInfoService: RestResourceInfoService,
  mapper: HandlerMapper,
  interpreter: TapirToPekkoInterpreter
) {

  val getResourcesInfoHandler =
    EndpointAndZioHandler[Unit, (String, String, Option[Order], Option[OrderBy]), ListResponseDto](
      endpoints.getResourcesInfo,
      { case (projectIri: String, resourceClass: String, order: Option[Order], orderBy: Option[OrderBy]) =>
        resourceInfoService.findByProjectAndResourceClass(
          projectIri,
          resourceClass,
          order.getOrElse(ASC),
          orderBy.getOrElse(lastModificationDate)
        )
      }
    )

  val routes: Seq[Route] = List(getResourcesInfoHandler)
    .map(it => mapper.mapEndpointAndHandler(it))
    .map(interpreter.toRoute(_))
}
object ResourceInfoRoutes {
  val layer = ZLayer.derive[ResourceInfoRoutes]
}
