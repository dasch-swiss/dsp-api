package org.knora.webapi.core

import org.knora.webapi.config.AppConfig
import zhttp.html._
import zhttp.http._
import zhttp.service.{EventLoopGroup, Server}
import zhttp.service.server.ServerChannelFactory
import zio.{Runtime, ZIO, ZLayer}
import zio.metrics.connectors.{MetricsConfig, prometheus}
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.jvm.DefaultJvmMetrics

object InstrumentationServer {

  private val nThreads = 5

  private lazy val indexPage =
    """<html>
      |<title>Simple Server</title>
      |<body>
      |<p><a href="/metrics">Prometheus Metrics</a></p>
      |</body
      |</html>""".stripMargin

  private lazy val static =
    Http.collect[Request] { case Method.GET -> !! => Response.html(Html.fromString(indexPage)) }

  private lazy val prometheusRouter =
    Http
      .collectZIO[Request] { case Method.GET -> !! / "metrics" =>
        ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
      }

  private def server(config: AppConfig): Server[PrometheusPublisher, Throwable] =
    Server.port(config.prometheusServerConfig.port) ++ Server.app(static ++ prometheusRouter)

  private def runHttpServer(config: AppConfig) =
    ZIO.logInfo("starting prometheus server in a separate root fiber.") *>
      server(config).start

  val make: ZIO[AppConfig, Throwable, Nothing] =
    ZIO
      .service[AppConfig]
      .flatMap(config =>
        runHttpServer(config)
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
            DefaultJvmMetrics.live.unit.orDie
          )
      )

}
