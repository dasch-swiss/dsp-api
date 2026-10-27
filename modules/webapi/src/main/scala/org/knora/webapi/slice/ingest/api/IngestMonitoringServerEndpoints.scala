/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import sttp.tapir.ztapir.*
import zio.*
import zio.metrics.connectors.prometheus.PrometheusPublisher

import org.knora.webapi.slice.ingest.infrastructure.HealthCheckService

/** `/ingest/metrics` serves the whole API process's metrics, the same data as the metrics server's `/metrics`. */
final class IngestMonitoringServerEndpoints(
  endpoints: IngestMonitoringEndpoints,
  healthService: HealthCheckService,
  metrics: PrometheusPublisher,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    endpoints.getInfo.zServerLogic(_ => ZIO.succeed(InfoEndpointResponse.instance)),
    endpoints.getHealth.zServerLogic(_ =>
      healthService.check.filterOrElseWith(_.isHealthy)(res => ZIO.fail(ApiProblem.Unhealthy.from(res))),
    ),
    endpoints.getMetrics.zServerLogic(_ => metrics.get),
  )
}

object IngestMonitoringServerEndpoints {
  val layer = ZLayer.derive[IngestMonitoringServerEndpoints]
}
