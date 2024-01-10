/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.slice.admin.api.service.GroupsRestService
import org.knora.webapi.slice.common.api.EndpointAndZioHandler
import org.knora.webapi.slice.common.api.HandlerMapper

case class GroupsEndpointsHandler(
  endpoints: GroupsEndpoints,
  restService: GroupsRestService,
  mapper: HandlerMapper
) {
  private val getGroupsHandler =
    EndpointAndZioHandler(
      endpoints.getGroups,
      (_: Unit) => restService.getGroups
    )

  val handlers = List(getGroupsHandler).map(mapper.mapEndpointAndHandler(_))
}

object GroupsEndpointsHandler {
  val layer = ZLayer.derive[GroupsEndpointsHandler]
}
