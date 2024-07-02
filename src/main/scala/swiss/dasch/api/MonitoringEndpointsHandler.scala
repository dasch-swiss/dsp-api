/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.tapir.ztapir.*
import swiss.dasch.infrastructure.HealthCheckService
import zio.*
import zio.metrics.connectors.prometheus.PrometheusPublisher

import scala.util.chaining.*

case class MonitoringEndpointsHandler(
  monitoringEndpoints: MonitoringEndpoints,
  healthService: HealthCheckService,
  metrics: PrometheusPublisher,
) {

  val infoEndpoint: ZServerEndpoint[Any, Any] =
    monitoringEndpoints.infoEndpoint.zServerLogic(_ => ZIO.succeed(InfoEndpointResponse.instance))

  val healthEndpoint: ZServerEndpoint[Any, Any] =
    monitoringEndpoints.healthEndpoint
      .zServerLogic(_ =>
        healthService.check.filterOrElseWith(_.isHealthy)(res => ZIO.fail(ApiProblem.Unhealthy.from(res))),
      )

  val metricsEndpoint: ZServerEndpoint[Any, Any] =
    monitoringEndpoints.metricsEndpoint.zServerLogic(_ => metrics.get)

  val endpoints: List[ZServerEndpoint[Any, Any]] = List(infoEndpoint, healthEndpoint, metricsEndpoint)
}
object MonitoringEndpointsHandler {
  val layer = ZLayer.derive[MonitoringEndpointsHandler]
}
