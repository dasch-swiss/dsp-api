/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.lists.api

import zio.*
import sttp.tapir.ztapir.*

import org.knora.webapi.slice.lists.api.service.ListsV2RestService

final case class ListsEndpointsV2Handler(
  private val endpoints: ListsEndpointsV2,
  private val restService: ListsV2RestService,
) {
  val allHandlers = Seq(
    endpoints.getV2Lists.serverLogic(restService.getList),
    endpoints.getV2Node.serverLogic(restService.getNode),
  )
}

object ListsEndpointsV2Handler {
  val layer = ZLayer.derive[ListsEndpointsV2Handler]
}
