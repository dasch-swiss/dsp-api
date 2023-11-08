/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure.api

import zio._
import zio.http._
import zio.metrics.connectors.prometheus.PrometheusPublisher

/**
 * Provides the '/metrics' endpoint serving the metrics in prometheus format.
 */
final case class PrometheusApp(prometheus: PrometheusPublisher) {
  val route: HttpApp[Any] = Routes(Method.GET / "metrics" -> handler(prometheus.get.map(Response.text(_)))).toHttpApp
}
object PrometheusApp {
  val layer = ZLayer.derive[PrometheusApp]
}
