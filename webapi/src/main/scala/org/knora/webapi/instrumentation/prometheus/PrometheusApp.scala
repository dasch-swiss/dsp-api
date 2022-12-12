package org.knora.webapi.instrumentation.prometheus

import zhttp.http._
import zhttp.service.Server
import zio.ZIO
import zio.metrics.connectors.prometheus.PrometheusPublisher

object PrometheusApp {

  private lazy val prometheusRouter =
    Http
      .collectZIO[Request] { case Method.GET -> !! / "metrics" =>
        ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
      }

  val make =
    Server.app(prometheusRouter)

}
