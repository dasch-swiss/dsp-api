/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.instrumentation.prometheus

import zhttp.http._
import zio.ZIO
import zio.ZLayer
import zio.metrics.connectors.prometheus.PrometheusPublisher

/**
 * Provides the '/metrics' endpoint serving the metrics in prometheus format.
 */
final case class PrometheusApp() {

  val route: HttpApp[PrometheusPublisher, Nothing] =
    Http
      .collectZIO[Request] { case Method.GET -> !! / "metrics" =>
        ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
      }
}
object PrometheusApp {
  val layer =
    ZLayer.succeed(PrometheusApp())
}
