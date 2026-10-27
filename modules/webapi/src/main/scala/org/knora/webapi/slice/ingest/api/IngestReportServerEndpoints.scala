/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import sttp.tapir.ztapir.*
import zio.ZLayer

final class IngestReportServerEndpoints(endpoints: IngestReportEndpoints, rest: IngestReportRestService) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.postAssetOverviewReport.serverLogic(_ => _ => rest.createAssetOverviewReport),
  )
}

object IngestReportServerEndpoints {
  val layer = ZLayer.derive[IngestReportServerEndpoints]
}
