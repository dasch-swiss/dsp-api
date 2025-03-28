/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure.api

import zio.*
import zio.http.*
import zio.metrics.connectors.prometheus.PrometheusPublisher

/**
 * Provides the '/metrics' endpoint serving the metrics in prometheus format.
 */
final case class PrometheusRoutes(prometheus: PrometheusPublisher) {
  val routes: Routes[Any, Nothing] = Routes(Method.GET / "metrics" -> handler(prometheus.get.map(Response.text)))
}
object PrometheusRoutes {
  val layer = ZLayer.derive[PrometheusRoutes]
}
