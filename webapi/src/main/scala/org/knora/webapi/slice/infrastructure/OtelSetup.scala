package org.knora.webapi.slice.infrastructure
import io.opentelemetry.api
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter
import io.opentelemetry.semconv.ServiceAttributes
import zio.*
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing

object OtelSetup {
  private def stdoutTracerProvider(resourceName: String): RIO[Scope, SdkTracerProvider] =
    for {
      spanExporter   <- ZIO.fromAutoCloseable(ZIO.succeed(OtlpJsonLoggingSpanExporter.create()))
      spanProcessor  <- ZIO.fromAutoCloseable(ZIO.succeed(SimpleSpanProcessor.create(spanExporter)))
      tracerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkTracerProvider
              .builder()
              .setResource(Resource.create(Attributes.of(ServiceAttributes.SERVICE_NAME, resourceName)))
              .addSpanProcessor(spanProcessor)
              .build(),
          ),
        )
    } yield tracerProvider

  def custom(resourceName: String): TaskLayer[api.OpenTelemetry] = {
    val traceparentPropagator = ContextPropagators.create(
      TextMapPropagator.composite(
        W3CTraceContextPropagator.getInstance,
      ),
    )
    OpenTelemetry.custom(
      for {
        tracerProvider <- stdoutTracerProvider(resourceName)
        openTelemetry  <-
          ZIO.fromAutoCloseable(
            ZIO.succeed(
              OpenTelemetrySdk
                .builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(traceparentPropagator)
                .buildAndRegisterGlobal,
            ),
          )
      } yield openTelemetry,
    )
  }

  val layer: ULayer[api.OpenTelemetry & Tracing & ContextStorage] =
    (OpenTelemetry.global <*> OpenTelemetry.contextZIO >+> OpenTelemetry.tracing("global")).orDie

  val stdOut: ULayer[api.OpenTelemetry & Tracing & ContextStorage] =
    (custom("dsp-api") <*> OpenTelemetry.contextZIO >+> OpenTelemetry.tracing("test")).orDie
}
