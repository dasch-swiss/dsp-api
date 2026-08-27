/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin
import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.api.admin.service.ViewRestrictionsByPropertyRestService

final class ViewRestrictionsByPropertyServerEndpoints(
  endpoints: ViewRestrictionsByPropertyEndpoints,
  restService: ViewRestrictionsByPropertyRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getProperties.serverLogic(restService.getProperties),
    endpoints.getPropertyValues.serverLogic(restService.getPropertyValues),
    endpoints.getPropertyItems.serverLogic(restService.getPropertyItems),
  )
}

object ViewRestrictionsByPropertyServerEndpoints {
  val layer = ZLayer.derive[ViewRestrictionsByPropertyServerEndpoints]
}
