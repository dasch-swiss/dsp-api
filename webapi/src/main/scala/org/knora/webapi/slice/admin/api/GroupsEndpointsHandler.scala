/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.messages.admin.responder.groupsmessages.GroupGetResponseADM
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupStatusUpdateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.admin.api.service.GroupRestService
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

case class GroupsEndpointsHandler(
  endpoints: GroupsEndpoints,
  restService: GroupRestService,
  mapper: HandlerMapper,
) {

  val allHandlers =
    List(
      PublicEndpointHandler(endpoints.getGroups, (_: Unit) => restService.getGroups),
      PublicEndpointHandler(endpoints.getGroupByIri, restService.getGroupByIri),
    ).map(mapper.mapPublicEndpointHandler) ++
      List(
        SecuredEndpointHandler(endpoints.getGroupMembers, restService.getGroupMembers),
        SecuredEndpointHandler(endpoints.postGroup, restService.postGroup),
        SecuredEndpointHandler(endpoints.putGroup, restService.putGroup),
        SecuredEndpointHandler(endpoints.putGroupStatus, restService.putGroupStatus),
        SecuredEndpointHandler(endpoints.deleteGroup, restService.deleteGroup),
      ).map(mapper.mapSecuredEndpointHandler)
}

object GroupsEndpointsHandler {
  val layer = ZLayer.derive[GroupsEndpointsHandler]
}
