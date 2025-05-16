/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.api

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*
import zio.ZLayer

import org.knora.webapi.slice.resources.api.service.StandoffRestService

final case class StandoffServerEndpoints(
  private val endpoints: StandoffEndpoints,
  private val standoffRestService: StandoffRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] =
    List(endpoints.postMapping.serverLogic(standoffRestService.createMapping))
}

object StandoffServerEndpoints {
  val layer = ZLayer.derive[StandoffServerEndpoints]
}
