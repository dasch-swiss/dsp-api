/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.api

import zio.*
import sttp.tapir.ztapir.*

case class ShaclServerEndpoints(
  private val shaclEndpoints: ShaclEndpoints,
  private val shaclApiService: ShaclApiService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    shaclEndpoints.validate.zServerLogic(shaclApiService.validate),
  )
}

object ShaclServerEndpoints {
  val layer = ZLayer.derive[ShaclServerEndpoints]
}
