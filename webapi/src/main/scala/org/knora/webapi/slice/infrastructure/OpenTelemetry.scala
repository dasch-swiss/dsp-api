/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import io.sentry.Sentry
import io.sentry.SentryOptions
import zio.*
import zio.telemetry.opentelemetry.OpenTelemetry as ZOpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing

import scala.jdk.CollectionConverters.*

import org.knora.webapi.config.KnoraApi
import org.knora.webapi.config.OpenTelemetryConfig
import org.knora.webapi.http.version.BuildInfo

object OpenTelemetry {
  // Sentry is used for distributed tracing.
  // The sentry-opentelemetry-agentless dependency adds opentelemetry-sdk-extension-autoconfigure which takes care of
  // configuring OpenTelemetry to work with Sentry.
  // This is triggered by creating a `AutoConfiguredOpenTelemetrySdk` and must happen before `Sentry.init`.
  // https://docs.sentry.io/platforms/java/opentelemetry/setup/agentless/#usage
  private val otelSdkSentry = ZLayer.scoped {
    for {
      _ <- ZIO.logInfo("Initializing OpenTelemetry.")
      otel <- ZIO.fromAutoCloseable(
                ZIO.succeed(
                  AutoConfiguredOpenTelemetrySdk
                    .builder()
                    .addPropertiesSupplier(() =>
                      Map(
                        "otel.logs.exporter"    -> "none",
                        "otel.metrics.exporter" -> "none",
                        "otel.traces.exporter"  -> "none",
                      ).asJava,
                    )
                    .build()
                    .getOpenTelemetrySdk,
                ),
              )
      config    <- ZIO.service[OpenTelemetryConfig]
      apiConfig <- ZIO.service[KnoraApi]
      _ = config.dsn.map { dsn =>
            Sentry.init { (options: SentryOptions) =>
              options.setDsn(dsn)
              options.setTracesSampleRate(1.0)
              options.setEnvironment(apiConfig.externalHost)
              // For each of our pre-release, we use a common snapshot version.
              val version = BuildInfo.version.replaceFirst("-.*", "-SNAPSHOT")
              options.setRelease(version)
            }
          }
    } yield otel
  }

  val layer: URLayer[OpenTelemetryConfig & KnoraApi, Tracing] =
    // Integrate the OpenTelemetry SDK into ZIO and expose as `Tracing`: https://zio.dev/zio-telemetry/opentelemetry/
    otelSdkSentry >+> ZOpenTelemetry.contextZIO >>> ZOpenTelemetry.tracing("global")
}
