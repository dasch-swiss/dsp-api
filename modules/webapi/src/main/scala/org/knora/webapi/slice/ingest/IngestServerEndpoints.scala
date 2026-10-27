/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.api.MaintenanceEndpointsHandler
import swiss.dasch.api.MonitoringEndpointsHandler
import swiss.dasch.api.ProjectsEndpointsHandler
import swiss.dasch.api.ReportEndpointsHandler
import swiss.dasch.infrastructure.IngestExecutor
import zio.ZLayer

/**
 * Ingest's own server endpoints, with their inputs, error bodies and token check, served under `/ingest/`. The server
 * logic of every endpoint except info, health and metrics runs on the ingest executor, and so do the fibers it forks.
 */
final class IngestServerEndpoints(
  monitoring: MonitoringEndpointsHandler,
  projects: ProjectsEndpointsHandler,
  maintenance: MaintenanceEndpointsHandler,
  reports: ReportEndpointsHandler,
  executor: IngestExecutor,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] =
    (monitoring.endpoints ++ (projects.endpoints ++ maintenance.endpoints ++ reports.endpoints).map(onIngestPool))
      .map(_.prependSecurityIn(IngestServerEndpoints.prefix))

  private def onIngestPool[C](se: ZServerEndpoint[Any, C]): ZServerEndpoint[Any, C] =
    ServerEndpoint(se.endpoint, se.securityLogic, m => p => i => executor.run(se.logic(m)(p)(i)))
}

object IngestServerEndpoints {
  val layer = ZLayer.derive[IngestServerEndpoints]

  private val prefix = "ingest"

  def isIngestRoute(ep: AnyEndpoint): Boolean = ep.showPathTemplate(showQueryParam = None).startsWith(s"/$prefix/")
}
