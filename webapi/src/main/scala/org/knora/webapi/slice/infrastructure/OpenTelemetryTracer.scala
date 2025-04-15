/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import io.opentelemetry.api.OpenTelemetry as Otel
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import io.sentry.Sentry
import zio.*
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing

import java.util.HashMap

import org.knora.webapi.config.AppConfig

object OpenTelemetryTracerLive {

  val layer: ZLayer[AppConfig, Nothing, Tracing] =
    ZLayer.fromZIO(
      sentry.provideSome[AppConfig](
        sdkLayer,
        contextStorageLayer,
        tracingLayer,
      ),
    )

  private val sdkLayer: ZLayer[AppConfig, Nothing, Otel] =
    ZLayer.fromZIO(for {
      appConfig <- ZIO.service[AppConfig]
      sdk = AutoConfiguredOpenTelemetrySdk
              .builder()
              .setResultAsGlobal()
              .addPropertiesSupplier { () =>
                val properties = new HashMap[String, String]()
                properties.put("otel.logs.exporter", "none");
                properties.put("otel.metrics.exporter", "none");
                properties.put("otel.traces.exporter", "none");
                properties
              }
              .build()
    } yield sdk.getOpenTelemetrySdk())

  private val contextStorageLayer: ULayer[ContextStorage] = OpenTelemetry.contextZIO

  private val tracingLayer: ZLayer[AppConfig & Otel & ContextStorage, Nothing, Tracing] =
    ZLayer
      .fromZIO(ZIO.service[AppConfig].map(_.openTelemetryTracer.serviceName))
      .flatMap(env => OpenTelemetry.tracing(env.get))

  private val sentry: ZIO[
    AppConfig & Otel & (ContextStorage & Tracing),
    Nothing,
    Tracing,
  ] =
    for {
      appConfig <- ZIO.service[AppConfig]
      sdk       <- ZIO.service[Otel]
      context   <- ZIO.service[ContextStorage]
      tracing   <- ZIO.service[Tracing]
      dsn        = appConfig.openTelemetryTracer.dsn
      _ = if (dsn.nonEmpty) {
            Sentry.init { options =>
              options.setDsn(appConfig.openTelemetryTracer.dsn)
              options.setTracesSampleRate(1.0)
            }
          }
    } yield tracing
}
