/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import sttp.tapir.server.interceptor.metrics.MetricsRequestInterceptor
import sttp.tapir.server.metrics.EndpointMetric
import sttp.tapir.server.metrics.Metric
import sttp.tapir.server.metrics.MetricLabels
import sttp.tapir.server.metrics.zio.ZioMetrics
import zio.*
import zio.metrics.Metric as ZioMetric
import zio.metrics.MetricKeyType.Histogram.Boundaries

/**
 * tapir's default request metrics, trimmed to what we read, because every label combination of the duration histogram
 * is a billed series per bucket:
 *
 *   - no `tapir_request_active` gauge: it is never decremented for requests that match no endpoint, so it only grows;
 *   - no `phase="headers"` duration samples: each was a twin of a `phase="body"` series, differing by < 0.1 ms;
 *   - fewer duration buckets than tapir's 18, keeping resolution where the traffic is (5 ms to 500 ms);
 *   - no metrics at all for the `/health` and `/version` probes.
 */
private[core] object RequestMetrics {

  private val ignoredPaths: Set[List[String]] = Set(List("health"), List("version"))

  private val durationBoundaries: Boundaries =
    Boundaries.fromChunk(Chunk(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10, 30, 60))

  private val namespace = ZioMetrics.DefaultNamespace
  private val labels    = MetricLabels.Default

  private val requestTotal = ZioMetrics.requestTotal[Task](namespace, labels)

  private val requestDuration = {
    val tapirDefault = ZioMetrics.requestDuration[Task](namespace, labels)
    Metric[Task, ZioMetric.Histogram[Double]](
      ZioMetric.histogram(s"${namespace}_request_duration_seconds", durationBoundaries),
      (req, histogram, monad) =>
        monad.map(tapirDefault.onRequest(req, histogram, monad))(_.copy(onResponseHeaders = None)),
    )
  }

  private def skippingIgnoredPaths[M](metric: Metric[Task, M]): Metric[Task, M] =
    Metric[Task, M](
      metric.metric,
      (req, m, monad) =>
        if ignoredPaths.contains(req.pathSegments) then monad.unit(EndpointMetric())
        else metric.onRequest(req, m, monad),
    )

  val interceptor: MetricsRequestInterceptor[Task] =
    ZioMetrics[Task](namespace, List(skippingIgnoredPaths(requestTotal), skippingIgnoredPaths(requestDuration)))
      .metricsInterceptor()
}
