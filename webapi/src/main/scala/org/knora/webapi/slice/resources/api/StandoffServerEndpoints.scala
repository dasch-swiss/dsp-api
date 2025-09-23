/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import zio.*
import sttp.tapir.ztapir.*

import org.knora.webapi.slice.resources.api.service.StandoffRestService

final case class StandoffServerEndpoints(
  private val endpoints: StandoffEndpoints,
  private val restService: StandoffRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.postMapping.serverLogic(restService.createMapping),
  )
}
object StandoffServerEndpoints {
  val layer = ZLayer.derive[StandoffServerEndpoints]
}
