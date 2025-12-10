/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.resources.info

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.*
import zio.ZLayer

import org.knora.webapi.slice.admin.api.model.Order
import org.knora.webapi.slice.admin.api.model.OrderBy
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.api.ApiV2
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class ResourceInfoEndpoints(baseEndpoints: BaseEndpoints) {
  val getResourcesInfo = baseEndpoints.publicEndpoint.get
    .in("v2" / "resources" / "info")
    .in(header[ProjectIri](ApiV2.Headers.xKnoraAcceptProject))
    .in(query[String]("resourceClass"))
    .in(Order.queryParam)
    .in(OrderBy.queryParam.default(OrderBy.LastModificationDate))
    .out(jsonBody[ListResponseDto])
    .description("Get information about resources of a specific class in a project. Publicly accessible.")
}

object ResourceInfoEndpoints {
  private[info] val layer = ZLayer.derive[ResourceInfoEndpoints]
}
