/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.lists.api

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.lists.api.service.ListsV2RestService

final case class ListsV2ServerEndpoints(
  private val endpoints: ListsEndpointsV2,
  private val restService: ListsV2RestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getV2Lists.serverLogic(restService.getList),
    endpoints.getV2Node.serverLogic(restService.getNode),
  )
}

object ListsV2ServerEndpoints {
  val layer = ZLayer.derive[ListsV2ServerEndpoints]
}
