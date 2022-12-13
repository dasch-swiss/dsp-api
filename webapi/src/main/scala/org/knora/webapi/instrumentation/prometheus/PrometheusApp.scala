package org.knora.webapi.instrumentation.prometheus

import zio.http.{Http, HttpApp, Request, Response}
import zio.http._
import zio.ZIO
import zio.http.model.Method
import zio.metrics.connectors.prometheus.PrometheusPublisher

/**
 * Provides the '/metrics' endpoint serving the metrics in prometheus format.
 */
object PrometheusApp {

  def apply(): HttpApp[PrometheusPublisher, Nothing] =
    Http
      .collectZIO[Request] { case Method.GET -> !! / "metrics" =>
        ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
      }
}
