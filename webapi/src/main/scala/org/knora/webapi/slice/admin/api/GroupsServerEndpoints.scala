/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*
import zio.ZLayer

import org.knora.webapi.slice.admin.api.GroupsRequests.GroupStatusUpdateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.admin.api.service.GroupRestService
import org.knora.webapi.slice.admin.domain.model.GroupIri

case class GroupsServerEndpoints(
  private val endpoints: GroupsEndpoints,
  private val restService: GroupRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    endpoints.getGroups.zServerLogic(restService.getGroups),
    endpoints.getGroupByIri.zServerLogic(restService.getGroupByIri),
    endpoints.getGroupMembers.serverLogic(restService.getGroupMembers),
    endpoints.postGroup.serverLogic(restService.postGroup),
    endpoints.putGroup.serverLogic(restService.putGroup),
    endpoints.putGroupStatus.serverLogic(restService.putGroupStatus),
    endpoints.deleteGroup.serverLogic(restService.deleteGroup),
  )
}

object GroupsServerEndpoints {
  val layer = ZLayer.derive[GroupsServerEndpoints]
}
