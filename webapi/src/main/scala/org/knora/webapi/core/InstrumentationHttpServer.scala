package org.knora.webapi.core

import zhttp.service.Server
import zio.Runtime
import zio.ZIO
import zio.ZLayer
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.prometheus
import zio.metrics.jvm.DefaultJvmMetrics

import org.knora.webapi.config.AppConfig
import org.knora.webapi.instrumentation.health.HealthApp
import org.knora.webapi.instrumentation.index.IndexApp
import org.knora.webapi.instrumentation.prometheus.PrometheusApp

object InstrumentationHttpServer {

  private val routes =
    for {
      index      <- ZIO.service[IndexApp].map(_.route)
      health     <- ZIO.service[HealthApp].map(_.route)
      prometheus <- ZIO.service[PrometheusApp].map(_.route)
    } yield index ++ health ++ prometheus

  private val run =
    for {
      config <- ZIO.service[AppConfig]
      r      <- routes
      _      <- Server.start(config.prometheusServerConfig.port, r).forkDaemon
      _      <- ZIO.logInfo(s"Starting instrumentation http server on port: ${config.prometheusServerConfig.port}")
    } yield ()

  val make: ZIO[AppConfig with State, Throwable, Unit] =
    ZIO
      .service[AppConfig]
      .flatMap(config =>
        run
          .provideSome[AppConfig with State](
            // HttpApp implementation layers
            IndexApp.layer,
            HealthApp.layer,
            PrometheusApp.layer,

            // Metrics config
            ZLayer.succeed(MetricsConfig(config.prometheusServerConfig.interval)),

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
