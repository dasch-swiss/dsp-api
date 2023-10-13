package org.knora.webapi.slice.resourceinfo.api

import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.zio._
import zio.ZLayer

import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.resourceinfo.api.model.ListResponseDto
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.Order
import org.knora.webapi.slice.resourceinfo.api.model.QueryParams.OrderBy

final case class ResourceInfoEndpoints(baseEndpoints: BaseEndpoints) {
  val getResourcesInfo = baseEndpoints.publicEndpoint.get
    .in("v2" / "resources" / "info")
    .in(header[String](RouteUtilV2.PROJECT_HEADER))
    .in(query[String]("resourceClass"))
    .in(query[Option[Order]](Order.queryParamKey))
    .in(query[Option[OrderBy]](OrderBy.queryParamKey))
    .out(jsonBody[ListResponseDto])
}

object ResourceInfoEndpoints {
  val layer = ZLayer.derive[ResourceInfoEndpoints]
}
