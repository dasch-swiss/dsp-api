/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdkBuilder
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
  // This is triggered by creating a `AutoConfiguredOpenTelemetrySdk`.
  // https://docs.sentry.io/platforms/java/opentelemetry/setup/agentless/#usage
  private val builder: AutoConfiguredOpenTelemetrySdkBuilder = AutoConfiguredOpenTelemetrySdk
    .builder()
    .addPropertiesSupplier(() =>
      Map(
        "otel.logs.exporter"    -> "none",
        "otel.metrics.exporter" -> "none",
        "otel.traces.exporter"  -> "none",
      ).asJava,
    )

  private def make(
    build: AutoConfiguredOpenTelemetrySdkBuilder => AutoConfiguredOpenTelemetrySdk,
  ): ZLayer[OpenTelemetryConfig & KnoraApi, Nothing, Tracing] =
    ZLayer.scoped(for {
      config    <- ZIO.service[OpenTelemetryConfig]
      apiConfig <- ZIO.service[KnoraApi]
      otel      <- ZIO.fromAutoCloseable(ZIO.succeed(build(builder).getOpenTelemetrySdk))
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
    } yield otel)
      >+> ZOpenTelemetry.contextZIO >>> ZOpenTelemetry.tracing("global")

  val live: URLayer[OpenTelemetryConfig & KnoraApi, Tracing] = make(_.setResultAsGlobal().build())
  val test: URLayer[OpenTelemetryConfig & KnoraApi, Tracing] = make(_.build())
}
