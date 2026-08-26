/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin
import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.api.admin.service.ViewRestrictionsRestService

final class ViewRestrictionsServerEndpoints(
  endpoints: ViewRestrictionsEndpoints,
  restService: ViewRestrictionsRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getViewRestrictionsClasses.serverLogic(restService.getClasses),
    endpoints.getViewRestrictionsValues.serverLogic(restService.getValues),
    endpoints.getViewRestrictionsSummary.serverLogic(restService.getSummary),
    endpoints.getViewRestrictionsItems.serverLogic(restService.getItems),
  )
}

object ViewRestrictionsServerEndpoints {
  val layer = ZLayer.derive[ViewRestrictionsServerEndpoints]
}
