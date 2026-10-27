/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import sttp.tapir.ztapir.*
import zio.ZLayer

final class IngestMaintenanceServerEndpoints(
  endpoints: IngestMaintenanceEndpoints,
  rest: IngestMaintenanceRestService,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.postMaintenanceAction.serverLogic(p => rest.runAction(p)(_, _)),
    endpoints.getNeedsTopLeftCorrection.serverLogic(p => _ => rest.needsTopLeftCorrection(p)),
    endpoints.getWasTopLeftCorrectionApplied.serverLogic(p => _ => rest.wasTopLeftCorrectionApplied(p)),
  )
}

object IngestMaintenanceServerEndpoints {
  val layer = ZLayer.derive[IngestMaintenanceServerEndpoints]
}
