/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import org.knora.webapi.instrumentation.InstrumentationSupport
import zio.{Chunk, Duration}
import zio.metrics.Metric.Counter
import zio.metrics.{Metric, MetricKeyType, MetricState}

import java.time.Instant
import java.time.temporal.ChronoUnit.MILLIS
import java.util.regex.Pattern

/**
 * Akka HTTP directives which can be wrapped around a [[akka.http.scaladsl.server.Route]]].
 */
trait AroundDirectives extends InstrumentationSupport {

  /**
   * When wrapped around a [[akka.http.scaladsl.server.Route]], logs the time it took for the route to run.
   */
  private val requestCounter: Counter[Long] = Metric.counter("http_request_count")
  private val requestTimer: Metric[MetricKeyType.Histogram, Duration, MetricState.Histogram] =
    Metric.timer("http_request_duration", MILLIS, Chunk(10, 100, 1_000, 10_000, 100_000))
  def logDuration(implicit runtime: zio.Runtime[Any]): Directive0 = extractRequestContext.flatMap { ctx =>
    val start = Duration.fromInstant(Instant.now)
    mapResponse { resp =>
      val duration = start minus Duration.fromInstant(Instant.now)
      val message  = s"[${resp.status.intValue()}] ${ctx.request.method.name} ${ctx.request.uri} took: ${duration}"
      if (resp.status.isFailure()) metricsLogger.warn(message) else metricsLogger.debug(message)

      val path          = replaceIris(ctx.request.uri.path.toString())
      val httpMethod    = ctx.request.method.name.toUpperCase()
      val responseCode  = s"${resp.status.intValue()}"
      val counterMetric = addTags(requestCounter, path, httpMethod, responseCode)
      val timerMetric   = addTags(requestTimer, path, httpMethod, responseCode)
      UnsafeZioRun.runOrThrow((counterMetric.increment *> timerMetric.update(duration)).ignore.as(resp))
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
