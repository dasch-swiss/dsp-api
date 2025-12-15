/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.client4.opentelemetry.zio.OpenTelemetryTracingZioBackend
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing

import scala.concurrent.duration.FiniteDuration

object TracingHttpClient {

  val layer: URLayer[Tracing, StreamBackend[Task, ZioStreams]] = build(BackendOptions.Default)

  def layer(connectionTimeout: FiniteDuration): URLayer[Tracing, StreamBackend[Task, ZioStreams]] =
    build(options = BackendOptions.Default.connectionTimeout(connectionTimeout))

  private def build(options: BackendOptions): URLayer[Tracing, StreamBackend[Task, ZioStreams]] = ZLayer
    .fromZIO(for {
      tracing    <- ZIO.service[Tracing]
      zioBackend <- HttpClientZioBackend(options = options)
    } yield OpenTelemetryTracingZioBackend(zioBackend, tracing))
    .orDie
}
