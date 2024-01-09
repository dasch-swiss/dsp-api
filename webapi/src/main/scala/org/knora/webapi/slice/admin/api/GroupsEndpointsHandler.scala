package org.knora.webapi.slice.admin.api

import org.knora.webapi.slice.admin.api.service.GroupsRestService
import org.knora.webapi.slice.common.api.{EndpointAndZioHandler, HandlerMapper}
import zio.ZLayer

case class GroupsEndpointsHandler(
  endpoints: GroupsEndpoints,
  restService: GroupsRestService,
  mapper: HandlerMapper
) {
  val getGroupsHandler =
    EndpointAndZioHandler(
      endpoints.getGroups,
      (_: Unit) => restService.getAllGroups
    )
}

object GroupsEndpointsHandler {
  val layer = ZLayer.derive[GroupsEndpointsHandler]
}
