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
      index      <- ZIO.service[IndexApp].map(_.route)
      health     <- ZIO.service[HealthRouteZ].map(_.route)
      prometheus <- ZIO.service[PrometheusApp].map(_.route)
      app         = index ++ health ++ prometheus
      _          <- Server.serve(app)
    } yield ()

  val make: ZIO[State with AppConfig, Throwable, Fiber.Runtime[Throwable, Unit]] =
    ZIO
      .service[AppConfig]
      .flatMap { config =>
        val port          = config.instrumentationServerConfig.port
        val serverConfig  = ServerConfig.default.port(port)
        val interval      = config.instrumentationServerConfig.interval
        val metricsConfig = MetricsConfig(interval)
        ZIO.logInfo(s"Starting instrumentation http server on port: $port") *>
          ZIO.debug(s"$serverConfig, $metricsConfig") *>
          instrumentationServer
            .provideSome[State](
              // HTTP Server
              ZLayer.succeed(serverConfig) >>> Server.live,
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
            .forkDaemon
      }
}
