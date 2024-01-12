/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.slice.admin.api.service.GroupsRestService
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.common.api.EndpointAndZioHandler
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointAndZioHandler

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

  private val getGroupHandler =
    EndpointAndZioHandler(
      endpoints.getGroup,
      (iri: GroupIri) => restService.getGroup(iri)
    )

  private val getGroupMembersHandler =
    SecuredEndpointAndZioHandler(
      endpoints.getGroupMembers,
      user => iri => restService.getGroupMembers(iri, user)
    )

  private val securedHandlers = List(getGroupMembersHandler).map(mapper.mapEndpointAndHandler(_))

  val handlers = List(getGroupsHandler, getGroupHandler).map(mapper.mapEndpointAndHandler(_))
    ++ securedHandlers
}

object GroupsEndpointsHandler {
  val layer = ZLayer.derive[GroupsEndpointsHandler]
}
