package org.knora.webapi.core

import org.knora.webapi.config.{AppConfig, PrometheusServerConfig}
import org.knora.webapi.instrumentation.health.HealthApp
import org.knora.webapi.instrumentation.index.IndexApp
import org.knora.webapi.instrumentation.prometheus.PrometheusApp
import zio.http.ServerConfig.LeakDetectionLevel
import zio.{Fiber, Runtime, ZIO, ZLayer}
import zio.http.{Http, HttpApp, Request, Response, Server, ServerConfig}
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.{MetricsConfig, prometheus}
import zio.metrics.jvm.DefaultJvmMetrics

object InstrumentationHttpServer {

  val nThreads = 5

  private def config(c: AppConfig) = ServerConfig.default
    .port(c.prometheusServerConfig.port)
    .leakDetection(LeakDetectionLevel.PARANOID)
    .maxThreads(nThreads)

  private def configLayer(c: AppConfig) = ServerConfig.live(config(c))

  private val routes: HttpApp[State with PrometheusPublisher, Nothing] =
    IndexApp() ++ PrometheusApp() ++ HealthApp()

  private def runInstrumentationHttpServer(config: AppConfig) =
    Server.install[State with PrometheusPublisher](routes).flatMap { port =>
      ZIO.logInfo(s"Starting instrumentation http server on port: $port")
    }

  val make: ZIO[AppConfig, Throwable, Fiber.Runtime[Throwable, Nothing]] =
    ZIO
      .service[AppConfig]
      .flatMap(config =>
        runInstrumentationHttpServer(config)
          .provideSome[State](
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
