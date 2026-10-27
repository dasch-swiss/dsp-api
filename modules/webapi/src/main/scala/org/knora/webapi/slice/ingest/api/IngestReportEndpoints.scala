/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import sttp.model.StatusCode
import sttp.tapir.ztapir.*
import zio.ZLayer

final class IngestReportEndpoints(base: IngestBaseEndpoints) {

  val postAssetOverviewReport = base.securedEndpoint.post
    .in("report" / "asset-overview")
    .out(stringBody)
    .out(statusCode(StatusCode.Accepted))
    .tag("maintenance")
    .description(
      "Generate asset overview reports for all projects. Authorization: admin scope required (SystemAdmins only).",
    )
}

object IngestReportEndpoints {
  val layer = ZLayer.derive[IngestReportEndpoints]
}
