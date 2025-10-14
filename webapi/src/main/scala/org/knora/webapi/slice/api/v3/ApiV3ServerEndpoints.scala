/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3

import sttp.tapir.ztapir.*
import zio.*

final case class ApiV3ServerEndpoints() {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List.empty[ZServerEndpoint[Any, Any]].map(_.tag("API v3"))
}

object ApiV3ServerEndpoints {
  val layer = ZLayer.derive[ApiV3ServerEndpoints]
}
