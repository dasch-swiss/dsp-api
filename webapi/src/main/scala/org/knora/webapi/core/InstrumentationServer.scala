/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio._
import zio.http._
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.prometheus
import zio.metrics.jvm.DefaultJvmMetrics

import org.knora.webapi.config.AppConfig
import org.knora.webapi.instrumentation.health.HealthRouteZ
import org.knora.webapi.instrumentation.index.IndexApp
import org.knora.webapi.instrumentation.prometheus.PrometheusApp

object InstrumentationServer {

  private val instrumentationServer =
    for {
      index      <- ZIO.serviceWith[IndexApp](_.route)
      health     <- ZIO.serviceWith[HealthRouteZ](_.route)
      prometheus <- ZIO.serviceWith[PrometheusApp](_.route)
      app         = index ++ health ++ prometheus
      _          <- Server.serve(app)
    } yield ()

  val make: ZIO[State with AppConfig, Throwable, Unit] =
    ZIO.serviceWithZIO[AppConfig] { config =>
      val port          = config.instrumentationServerConfig.port
      val interval      = config.instrumentationServerConfig.interval
      val metricsConfig = MetricsConfig(interval)
      ZIO.logInfo(s"Starting instrumentation http server on http://localhost:$port") *>
        instrumentationServer
          .provideSome[State](
            // HTTP Server
            Server.defaultWithPort(port),
            // HTTP routes
            IndexApp.layer,
            HealthRouteZ.layer,
            PrometheusApp.layer,
            // Metrics dependencies
            prometheus.publisherLayer,
            ZLayer.succeed(metricsConfig) >>> prometheus.prometheusLayer,
            Runtime.enableRuntimeMetrics,
            Runtime.enableFiberRoots,
            DefaultJvmMetrics.live.unit
          )
    }
}
