/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import io.opentelemetry.api
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.ServiceAttributes
import zio.*
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing

import scala.jdk.CollectionConverters.*

/**
 * Test telemetry backed by an OpenTelemetry [[InMemorySpanExporter]]. Mirrors
 * `org.knora.webapi.slice.infrastructure.OtelSetup.stdOut` but captures finished spans in memory
 * instead of writing them to stdout, so tests can assert on the spans an effect produces.
 *
 * Provide [[layer]] to a spec exactly the way specs provide `OtelSetup.stdOut`, run the traced
 * effect, then read the captured spans with [[finishedSpans]] and assert on them with
 * [[SpanAssertions]].
 *
 * A [[SimpleSpanProcessor]] is used so spans are exported synchronously when they end - finished
 * spans are visible to [[finishedSpans]] immediately after the traced effect completes, with no
 * flush step.
 */
object InMemoryTracing {

  private val exporterLayer: ULayer[InMemorySpanExporter] =
    ZLayer.scoped(ZIO.fromAutoCloseable(ZIO.succeed(InMemorySpanExporter.create())))

  private val openTelemetryLayer: URLayer[InMemorySpanExporter, api.OpenTelemetry] = {
    val traceparentPropagator = ContextPropagators.create(
      TextMapPropagator.composite(
        W3CTraceContextPropagator.getInstance,
      ),
    )
    ZLayer.scoped {
      for {
        exporter       <- ZIO.service[InMemorySpanExporter]
        spanProcessor  <- ZIO.fromAutoCloseable(ZIO.succeed(SimpleSpanProcessor.create(exporter)))
        tracerProvider <-
          ZIO.fromAutoCloseable(
            ZIO.succeed(
              SdkTracerProvider
                .builder()
                .setResource(Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, "dsp-api-test")))
                .addSpanProcessor(spanProcessor)
                .build(),
            ),
          )
        openTelemetry <-
          ZIO.fromAutoCloseable(
            ZIO.succeed(
              OpenTelemetrySdk
                .builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(traceparentPropagator)
                .build,
            ),
          )
      } yield openTelemetry
    }
  }

  val layer: ULayer[api.OpenTelemetry & Tracing & ContextStorage & InMemorySpanExporter] =
    ZLayer.make[api.OpenTelemetry & Tracing & ContextStorage & InMemorySpanExporter](
      exporterLayer,
      openTelemetryLayer,
      OpenTelemetry.contextZIO,
      OpenTelemetry.tracing("dsp-api-test"),
    )

  /**
   * Like [[layer]] but backed by an externally supplied exporter, so a caller can hold the exporter
   * reference and read [[InMemorySpanExporter#getFinishedSpanItems]] directly (e.g. when wiring this as the
   * application's OTel layer in an end-to-end spec, where the exporter is not exposed in the test env).
   */
  def layerFor(exporter: InMemorySpanExporter): ULayer[api.OpenTelemetry & Tracing & ContextStorage] =
    ZLayer.make[api.OpenTelemetry & Tracing & ContextStorage](
      ZLayer.succeed(exporter),
      openTelemetryLayer,
      OpenTelemetry.contextZIO,
      OpenTelemetry.tracing("dsp-api-test"),
    )

  /** All spans that have finished (and been exported) so far, in completion order. */
  val finishedSpans: URIO[InMemorySpanExporter, Chunk[SpanData]] =
    ZIO.serviceWith[InMemorySpanExporter](exporter => Chunk.fromIterable(exporter.getFinishedSpanItems.asScala))

  /** Discard all captured spans - useful to isolate scenarios that share a single layer instance. */
  val reset: URIO[InMemorySpanExporter, Unit] =
    ZIO.serviceWith[InMemorySpanExporter](_.reset())
}
