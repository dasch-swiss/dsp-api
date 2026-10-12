/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.api

import sttp.tapir.ztapir.*
import zio.*

/** Wires the FAIR endpoints to [[FairRestService]]; HEAD is the landing answer without its body. */
final class FairServerEndpoints(endpoints: FairEndpoints, rest: FairRestService) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getLanding.zServerLogic { case (shortcode, id, accept) => rest.landing(shortcode, id, accept) },
    endpoints.headLanding.zServerLogic { case (shortcode, id, accept) =>
      rest.landing(shortcode, id, accept).map(_.copy(body = None))
    },
    endpoints.getJsonLd.zServerLogic { case (shortcode, id) => rest.jsonLd(shortcode, id) },
    endpoints.getDataCite.zServerLogic { case (shortcode, id) => rest.dataCite(shortcode, id) },
  )
}

object FairServerEndpoints {
  val layer: URLayer[FairEndpoints & FairRestService, FairServerEndpoints] = ZLayer.derive[FairServerEndpoints]
}
