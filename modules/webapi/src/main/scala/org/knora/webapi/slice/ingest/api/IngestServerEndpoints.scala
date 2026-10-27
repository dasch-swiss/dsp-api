/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*
import zio.*

final class IngestServerEndpoints(
  projects: IngestProjectsServerEndpoints,
  maintenance: IngestMaintenanceServerEndpoints,
  reports: IngestReportServerEndpoints,
  monitoring: IngestMonitoringServerEndpoints,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] =
    monitoring.serverEndpoints ++ projects.serverEndpoints ++ maintenance.serverEndpoints ++ reports.serverEndpoints
}

object IngestServerEndpoints {
  val layer = ZLayer.derive[IngestServerEndpoints]
}
