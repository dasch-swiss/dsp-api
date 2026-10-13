/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.client4.opentelemetry.zio.OpenTelemetryTracingZioBackend
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing

import java.net.http.HttpClient
import scala.concurrent.duration.FiniteDuration

object TracingHttpClient {

  val layer: URLayer[Tracing, StreamBackend[Task, ZioStreams]] = build(BackendOptions.Default)

  def layer(connectionTimeout: FiniteDuration): URLayer[Tracing, StreamBackend[Task, ZioStreams]] =
    build(options = BackendOptions.Default.connectionTimeout(connectionTimeout))

  /**
   * A backend whose client speaks HTTP/1.1 only. Over plain `http`, the default client opens with an `Upgrade: h2c`
   * request, which some servers (the Angular dev server among them) never answer.
   */
  def http11Layer(connectionTimeout: FiniteDuration): URLayer[Tracing, StreamBackend[Task, ZioStreams]] =
    ZLayer.fromZIO(
      ZIO.serviceWith[Tracing] { tracing =>
        val client = HttpClient
          .newBuilder()
          .version(HttpClient.Version.HTTP_1_1)
          .connectTimeout(java.time.Duration.ofMillis(connectionTimeout.toMillis))
          .build()
        OpenTelemetryTracingZioBackend(HttpClientZioBackend.usingClient(client), tracing)
      },
    )

  private def build(options: BackendOptions): URLayer[Tracing, StreamBackend[Task, ZioStreams]] = ZLayer
    .fromZIO(for {
      tracing    <- ZIO.service[Tracing]
      zioBackend <- HttpClientZioBackend(options = options)
    } yield OpenTelemetryTracingZioBackend(zioBackend, tracing))
    .orDie
}
