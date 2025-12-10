/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupStatusUpdateRequest
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.api.admin.service.GroupRestService

final class GroupsServerEndpoints(
  endpoints: GroupsEndpoints,
  restService: GroupRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getGroups.zServerLogic(_ => restService.getGroups),
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
