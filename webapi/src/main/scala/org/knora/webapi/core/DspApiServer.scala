/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import io.opentelemetry.api.common.Attributes
import sttp.model.Method.*
import sttp.tapir.server.interceptor.cors.CORSConfig
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import zio.*
import zio.http.*
import zio.http.Server.Config.ResponseCompressionConfig
import zio.telemetry.opentelemetry.tracing.Tracing

import org.knora.webapi.config.KnoraApi
import org.knora.webapi.routing.Endpoints

final case class DspApiServer(
  server: Server,
  endpoints: Endpoints,
  c: KnoraApi,
  tracing: Tracing,
) {
  val addUrlRouteSpan: Middleware[Any] =
    new Middleware[Any] {
      override def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
        routes.transform { handler =>
          Handler.scoped[Env1] {
            Handler.fromFunctionZIO { request =>
              (for {
                span     <- tracing.getCurrentSpanUnsafe // TODO: requires handling an exception?
                traceId   = span.getSpanContext().getTraceId
                response <- ZIO.logAnnotate("traceId", traceId)(handler(request))
              } yield response)
                @@ tracing.aspects.span(
                  spanName = "root",
                  attributes = Attributes
                    .builder()
                    .put("url.route", request.path.toString)
                    .build(),
                )
            }
          }
        }
    }

  private val serverOptions: ZioHttpServerOptions[Any] =
    ZioHttpServerOptions.customiseInterceptors
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
    baseApp     = ZioHttpInterpreter(serverOptions).toHttp(endpoints.serverEndpoints)
    app         = baseApp @@ addUrlRouteSpan
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
