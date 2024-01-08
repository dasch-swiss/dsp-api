/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import zio.*
import zio.http.*
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.prometheus
import zio.metrics.jvm.DefaultJvmMetrics

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.State
import org.knora.webapi.slice.infrastructure.api.PrometheusApp

object MetricsServer {

  private val metricsServer = ZIO.serviceWithZIO[PrometheusApp](app => Server.install(app.route)) *> ZIO.never

  val make: ZIO[State & AppConfig, Throwable, Unit] =
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
