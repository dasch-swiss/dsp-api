/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.monitoring

import zio.*
import zio.http.*
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.{ MetricsConfig, prometheus }
import zio.metrics.jvm.DefaultJvmMetrics

object MetricsEndpoint {
  val app = Http.collectZIO[Request] {
    case Method.GET -> Root / "metrics" =>
      ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
  }
}

object Metrics {
  val layer: ZLayer[Any, Nothing, PrometheusPublisher] =
    ZLayer.make[PrometheusPublisher](
      ZLayer.succeed(MetricsConfig(interval = 5.seconds)),
      prometheus.publisherLayer,
      prometheus.prometheusLayer,
      DefaultJvmMetrics.live.unit.orDie,
    )
}
