/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.resourceinfo.api.model.ListResponseDto
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.Asc
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.LastModificationDate
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.Order
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.OrderBy
import org.knora.webapi.slice.resourceinfo.api.service.RestResourceInfoService

final case class ResourceInfoRoutes(
  endpoints: ResourceInfoEndpoints,
  resourceInfoService: RestResourceInfoService,
  mapper: HandlerMapper,
  interpreter: TapirToPekkoInterpreter
) {

  val getResourcesInfoHandler =
    PublicEndpointHandler[(IriIdentifier, String, Option[Order], Option[OrderBy]), ListResponseDto](
      endpoints.getResourcesInfo,
      { case (projectIri: IriIdentifier, resourceClass: String, order: Option[Order], orderBy: Option[OrderBy]) =>
        resourceInfoService.findByProjectAndResourceClass(
          projectIri,
          resourceClass,
          order.getOrElse(Asc),
          orderBy.getOrElse(LastModificationDate)
        )
      }
    )

  val routes: Seq[Route] = List(getResourcesInfoHandler)
    .map(it => mapper.mapPublicEndpointHandler(it))
    .map(interpreter.toRoute(_))
}
object ResourceInfoRoutes {
  val layer = ZLayer.derive[ResourceInfoRoutes]
}
