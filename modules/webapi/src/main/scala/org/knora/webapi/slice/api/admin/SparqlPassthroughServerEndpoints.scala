/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.api.admin.service.SparqlPassthroughRestService

/**
 * Registers the passthrough route only where the deployment has opted in. When the flag is off the list is empty, so
 * the route does not exist at all: requests get a `404`, and the endpoint is also absent from the generated API
 * documentation. Enabling the flag therefore both opens the route and advertises it, which is why a break-glass window
 * on a production deployment should be time-boxed.
 */
final class SparqlPassthroughServerEndpoints(
  appConfig: AppConfig,
  endpoints: SparqlPassthroughEndpoints,
  restService: SparqlPassthroughRestService,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] =
    if (appConfig.allowSparqlPassthrough)
      List(endpoints.postAdminSparqlQuery.serverLogic(restService.query))
    else List.empty
}

object SparqlPassthroughServerEndpoints {
  val layer = ZLayer.derive[SparqlPassthroughServerEndpoints]
}
