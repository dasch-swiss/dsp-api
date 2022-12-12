package org.knora.webapi.core

import org.knora.webapi.config.AppConfig
import org.knora.webapi.instrumentation.health.HealthApp
import org.knora.webapi.instrumentation.index.IndexApp
import org.knora.webapi.instrumentation.prometheus.PrometheusApp
import zhttp.service.EventLoopGroup
import zhttp.service.Server
import zhttp.service.server.ServerChannelFactory
import zio.Fiber
import zio.Runtime
import zio.ZIO
import zio.ZLayer
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.prometheus
import zio.metrics.jvm.DefaultJvmMetrics

object InstrumentationHttpServer {

  private val nThreads = 5

  private val routes =
    IndexApp() ++ PrometheusApp() ++ HealthApp()

  private def runInstrumentationHttpServer(config: AppConfig) =
    ZIO.logInfo("Starting instrumentation http server") *>
      Server.start(config.prometheusServerConfig.port, routes).forkDaemon

  val make: ZIO[AppConfig, Throwable, Fiber.Runtime[Throwable, Nothing]] =
    ZIO
      .service[AppConfig]
      .flatMap(config =>
        runInstrumentationHttpServer(config)
          .provide(
            ServerChannelFactory.auto,
            EventLoopGroup.auto(nThreads),

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
