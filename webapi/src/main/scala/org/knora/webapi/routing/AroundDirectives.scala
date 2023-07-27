/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import zio.Chunk
import zio.Duration
import zio.metrics.Metric
import zio.metrics.Metric.Counter
import zio.metrics.MetricKeyType
import zio.metrics.MetricState

import java.time.temporal.ChronoUnit.MILLIS
import java.util.regex.Pattern

import org.knora.webapi.instrumentation.InstrumentationSupport

/**
 * Akka HTTP directives which can be wrapped around a [[akka.http.scaladsl.server.Route]]].
 */
trait AroundDirectives extends InstrumentationSupport {

  /**
   * When wrapped around a [[akka.http.scaladsl.server.Route]], logs the time it took for the route to run.
   */
  private val requestCounter: Counter[Long] = Metric.counter("http_request_count")
  private val requestTimer: Metric[MetricKeyType.Histogram, Duration, MetricState.Histogram] =
    Metric.timer("http_request_duration", MILLIS, Chunk.iterate(1.0, 5)(_ * 10))
  def logDuration(implicit runtime: zio.Runtime[Any]): Directive0 = extractRequestContext.flatMap { ctx =>
    val start = System.currentTimeMillis()
    mapResponse { resp =>
      val duration = System.currentTimeMillis() - start
      val message  = s"[${resp.status.intValue()}] ${ctx.request.method.name} ${ctx.request.uri} took: ${duration}ms"
      if (resp.status.isFailure()) metricsLogger.warn(message) else metricsLogger.debug(message)

      val path          = replaceIris(ctx.request.uri.path.toString())
      val httpMethod    = ctx.request.method.name.toUpperCase()
      val responseCode  = s"${resp.status.intValue()}"
      val counterMetric = addTags(requestCounter, path, httpMethod, responseCode)
      val timerMetric   = addTags(requestTimer, path, httpMethod, responseCode)
      UnsafeZioRun.runOrThrow(
        (counterMetric.increment *> timerMetric.update(Duration.fromMillis(duration))).ignore.as(resp)
      )
    }
  }

  private def addTags[A, B, C](metric: Metric[A, B, C], path: String, method: String, code: String): Metric[A, B, C] =
    metric.tagged("method", method).tagged("code", code).tagged("path", path)

  private val iriPattern = Pattern.compile(".*?/(https?:%2F%2F[^/]+)")
  private def replaceIris(path: String) = {
    val matcher = iriPattern.matcher(path)
    if (matcher.find()) {
      val iri = matcher.group(1)
      path.replace(iri, "__IRI__")
    } else {
      path
    }
  }
}
