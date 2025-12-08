/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import io.opentelemetry.api.OpenTelemetry
import sttp.model.Method.*
import sttp.tapir.server.interceptor.cors.CORSConfig
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.tracing.opentelemetry.OpenTelemetryTracing
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import zio.*
import zio.http.*
import zio.http.Server.Config.ResponseCompressionConfig

import org.knora.webapi.config.KnoraApi
import org.knora.webapi.slice.api.Endpoints

final class DspApiServer(
  server: Server,
  endpoints: Endpoints,
  c: KnoraApi,
  otel: OpenTelemetry,
) {

  private val serverOptions: ZioHttpServerOptions[Any] =
    ZioHttpServerOptions.customiseInterceptors
      .prependInterceptor(OpenTelemetryTracing(otel))
      .corsInterceptor(
        CORSInterceptor.customOrThrow(
          CORSConfig.default.allowCredentials
            .allowMethods(GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH)
            .allowMatchingOrigins(_ => true)
            .exposeAllHeaders
            .maxAge(30.minutes.asScala),
        ),
      )
      .metricsInterceptor(ZioMetrics.default[Task]().metricsInterceptor())
      .options

  def startup(): UIO[Unit] = for {
    _          <- ZIO.logInfo("Starting DSP API server...")
    app         = ZioHttpInterpreter(serverOptions).toHttp(endpoints.serverEndpoints)
    actualPort <- Server.install(app).provide(ZLayer.succeed(server))
    _          <- ZIO.logInfo(s"API available at http://${c.externalHost}:$actualPort/version")
  } yield ()
}

object DspApiServer {

  def startup: RIO[DspApiServer, Unit] = ZIO.serviceWithZIO[DspApiServer](_.startup())

  private val serverLayer = ZLayer
    .service[KnoraApi]
    .flatMap(cfg =>
      Server
        .defaultWith(
          _.binding(cfg.get.internalHost, cfg.get.internalPort).enableRequestStreaming
            .responseCompression(ResponseCompressionConfig.default),
        ),
    )
    .orDie

  val layer = serverLayer >>> ZLayer.derive[DspApiServer]
}
