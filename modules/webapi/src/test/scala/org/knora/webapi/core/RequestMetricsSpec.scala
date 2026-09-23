/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.junit.runner.RunWith
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import sttp.tapir.ztapir.*
import zio.*
import zio.http.Request
import zio.http.URL
import zio.metrics.Metric as ZioMetric
import zio.metrics.MetricKeyType.Histogram.Boundaries
import zio.metrics.MetricLabel
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

/**
 * The request metrics as the server records them: requests are run in memory through a tapir interpreter carrying
 * [[RequestMetrics.interceptor]], and the ZIO metric registry is read back. Each test uses its own path, because the
 * registry is global.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class RequestMetricsSpec extends ZIOSpecDefault {

  private val boundaries =
    Boundaries.fromChunk(Chunk(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10, 30, 60))

  private def okEndpoint(segment: String) =
    endpoint.get.in(segment).out(stringBody).zServerLogic[Any](_ => ZIO.succeed("ok"))

  private val routes = ZioHttpInterpreter(
    ZioHttpServerOptions.customiseInterceptors.metricsInterceptor(RequestMetrics.interceptor).options,
  ).toHttp(List(okEndpoint("metrics-body"), okEndpoint("metrics-total"), okEndpoint("health"), okEndpoint("version")))

  private def get(path: String) =
    routes.runZIO(Request.get(URL.decode(path).getOrElse(throw new IllegalArgumentException(path))))

  private def labels(path: String, extra: (String, String)*): Set[MetricLabel] =
    (Seq("method" -> "GET", "path" -> path, "status" -> "2xx") ++ extra).map(MetricLabel(_, _)).toSet

  private def durationCount(path: String, phase: String) =
    ZioMetric
      .histogram("tapir_request_duration_seconds", boundaries)
      .tagged(labels(path, "phase" -> phase))
      .value
      .map(_.count)

  private def totalCount(path: String) =
    ZioMetric.counter("tapir_request_total").tagged(labels(path)).value.map(_.count)

  override val spec = suite("RequestMetrics")(
    test("records the duration once, in the body phase, with our bucket boundaries") {
      for {
        _       <- get("/metrics-body")
        body    <- durationCount("/metrics-body", "body")
        headers <- durationCount("/metrics-body", "headers")
      } yield assertTrue(body == 1L, headers == 0L)
    },
    test("counts requests") {
      for {
        _     <- get("/metrics-total")
        _     <- get("/metrics-total")
        total <- totalCount("/metrics-total")
      } yield assertTrue(total == 2.0)
    },
    test("records nothing for the /health and /version probes") {
      for {
        _        <- get("/health")
        _        <- get("/version")
        health   <- totalCount("/health")
        version  <- totalCount("/version")
        healthD  <- durationCount("/health", "body")
        versionD <- durationCount("/version", "body")
      } yield assertTrue(health == 0.0, version == 0.0, healthD == 0L, versionD == 0L)
    },
  )
}
