/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.State
import org.knora.webapi.slice.infrastructure.api.PrometheusApp
import zio._
import zio.http._
import zio.metrics.connectors.{MetricsConfig, prometheus}
import zio.metrics.jvm.DefaultJvmMetrics

object MetricsServer {

  private val metricsServer = ZIO.serviceWith[PrometheusApp](_.route).flatMap(Server.install(_)) *> ZIO.never

  val make: ZIO[State with AppConfig, Throwable, Unit] =
    ZIO.serviceWithZIO[AppConfig] { config =>
      val port          = config.instrumentationServerConfig.port
      val interval      = config.instrumentationServerConfig.interval
      val metricsConfig = MetricsConfig(interval)
      ZIO.logInfo(s"Starting instrumentation http server on http://localhost:$port") *>
        metricsServer
          .provideSome[State](
            Server.defaultWithPort(port),
            prometheus.publisherLayer,
            ZLayer.succeed(metricsConfig) >>> prometheus.prometheusLayer,
            Runtime.enableRuntimeMetrics,
            Runtime.enableFiberRoots,
            DefaultJvmMetrics.live.unit,
            PrometheusApp.layer
          )
    }
}
