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

import org.knora.webapi.slice.api.v3.projects.ClosesOnUnreadBody
import org.knora.webapi.slice.api.v3.projects.ClosesOnUnreadBodyRoute
import org.knora.webapi.slice.common.api.Policy

/**
 * Ingest's own server endpoints, with their inputs, error bodies and token check, served under `/ingest/`. The server
 * logic of every endpoint except info, health and metrics runs on the ingest executor, and so do the fibers it forks.
 * Every route carries [[ClosesOnUnreadBody]], since uploads can be rejected before their body is read, and declares the
 * [[Policy]] its handler enforces through ingest's token scopes.
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
      .map(se => closesOnUnreadBody(withPolicy(se).prependSecurityIn(IngestServerEndpoints.prefix)))

  private def onIngestPool[C](se: ZServerEndpoint[Any, C]): ZServerEndpoint[Any, C] =
    ServerEndpoint(se.endpoint, se.securityLogic, m => p => i => executor.run(se.logic(m)(p)(i)))

  private def withPolicy[C](se: ZServerEndpoint[Any, C]): ZServerEndpoint[Any, C] =
    IngestServerEndpoints
      .policyOf(se.endpoint)
      .fold(se)(policy => ServerEndpoint(Policy.declaredOn(se.endpoint, policy), se.securityLogic, se.logic))

  private def closesOnUnreadBody[C](se: ZServerEndpoint[Any, C]): ZServerEndpoint[Any, C] =
    ServerEndpoint(se.endpoint.attribute(ClosesOnUnreadBody.key, ClosesOnUnreadBodyRoute), se.securityLogic, se.logic)
}

object IngestServerEndpoints {
  val layer = ZLayer.derive[IngestServerEndpoints]

  // ResourceMetadataRules.originalFileUrl builds links under this prefix
  private val prefix = "ingest"

  private val projectFromShortcode = "shortcode path parameter"

  // Mirrors the AuthorizationHandler check in each handler: the admin scope is SystemAdmin, a project's write scope
  // is held by its ProjectAdmins and by users who may create resources in it (ScopeResolver). An endpoint missing here
  // fails EndpointPolicyE2ESpec.
  private def policyOf(ep: AnyEndpoint): Option[Policy] = {
    val method = ep.method.fold("")(_.method)
    val path   = ep.showPathTemplate(showQueryParam = None, showQueryParamsAs = None)
    (method, path) match {
      case ("GET", "/info" | "/health" | "/metrics") =>
        Some(Policy.Public("Monitoring of the ingest service, read by the infrastructure."))
      case ("GET", "/projects/{shortcode}/assets/{assetId}/original") =>
        Some(Policy.ObjectAccess("the API's access decision on the asset's file value", admitsAnonymous = true))
      case ("GET", "/projects") | ("DELETE", "/projects/{shortcode}/erase") |
          ("POST", "/projects/{shortcode}/export" | "/projects/{shortcode}/import" | "/report/asset-overview") =>
        Some(Policy.SystemAdmin)
      case (_, p) if p.startsWith("/maintenance/")         => Some(Policy.SystemAdmin)
      case (_, p) if p.startsWith("/projects/{shortcode}") => Some(Policy.ProjectMember(projectFromShortcode))
      case _                                               => None
    }
  }

  def isIngestRoute(ep: AnyEndpoint): Boolean = ep.showPathTemplate(showQueryParam = None).startsWith(s"/$prefix/")
}
