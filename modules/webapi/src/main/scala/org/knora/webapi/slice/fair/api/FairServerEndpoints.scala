/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.api

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.config.FairConfig

/**
 * Wires the FAIR endpoints to [[FairRestService]]; HEAD is the landing answer without its body. The list is empty
 * unless `app.fair.app-external-base-url` is configured, so an unconfigured deployment never serves representations
 * whose URLs point at the wrong host.
 */
final class FairServerEndpoints(config: FairConfig, endpoints: FairEndpoints, rest: FairRestService) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] =
    if (config.appExternalBaseUrl.isDefined) enabled else List.empty

  private def enabled: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getLanding.zServerLogic { case (shortcode, id, accept) => rest.landing(shortcode, id, accept) },
    endpoints.headLanding.zServerLogic { case (shortcode, id, accept) =>
      rest.landing(shortcode, id, accept).map(_.copy(body = None))
    },
    endpoints.getJsonLd.zServerLogic { case (shortcode, id) => rest.jsonLd(shortcode, id) },
    endpoints.getDataCite.zServerLogic { case (shortcode, id) => rest.dataCite(shortcode, id) },
  )
}

object FairServerEndpoints {
  val layer: URLayer[FairConfig & FairEndpoints & FairRestService, FairServerEndpoints] =
    ZLayer.derive[FairServerEndpoints]
}
