/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.lists

import sttp.tapir.ztapir.*
import zio.*

final class ListsV2ServerEndpoints(endpoints: ListsEndpointsV2, restService: ListsV2RestService) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getV2Lists.serverLogic(restService.getList),
    endpoints.getV2Node.serverLogic(restService.getNode),
  )
}

object ListsV2ServerEndpoints {
  val layer =
    ListsEndpointsV2.layer >+> ListsV2RestService.layer >>> ZLayer.derive[ListsV2ServerEndpoints]
}
