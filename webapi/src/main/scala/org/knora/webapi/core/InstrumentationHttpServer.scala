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

object InstrumentationHttpServer {

  private val routes =
    for {
      index      <- ZIO.service[IndexApp].map(_.route)
      health     <- ZIO.service[HealthRouteZ].map(_.route)
      prometheus <- ZIO.service[PrometheusApp].map(_.route)
    } yield index ++ health ++ prometheus

  private val run =
    for {
      config      <- ZIO.service[AppConfig]
      r           <- routes
      serverConfig = ZLayer.succeed(ServerConfig.default.port(config.instrumentationServerConfig.port))
      _ <- Server
             .serve(r)
             .provideSome[State with prometheus.PrometheusPublisher](Server.live, serverConfig)
             .forkDaemon
      _ <- ZIO.logInfo(s"Starting instrumentation http server on port: ${config.instrumentationServerConfig.port}")
    } yield ()

  val make: ZIO[AppConfig with State, Throwable, Unit] =
    ZIO
      .service[AppConfig]
      .flatMap(config =>
        run
          .provideSome[AppConfig with State](
            // HttpApp implementation layers
            IndexApp.layer,
            HealthRouteZ.layer,
            PrometheusApp.layer,

            // Metrics config
            ZLayer.succeed(MetricsConfig(config.instrumentationServerConfig.interval)),

            // The prometheus reporting layer
            prometheus.publisherLayer,
            prometheus.prometheusLayer,

            // Enable the ZIO internal metrics and the default JVM metricsConfig
            // Do NOT forget the .unit for the JVM metrics layer
            Runtime.enableRuntimeMetrics,
            Runtime.enableFiberRoots,
            DefaultJvmMetrics.live.unit
          )
      )

}
