/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import sttp.tapir.server.interceptor.cors.CORSConfig
import sttp.tapir.server.interceptor.cors.CORSConfig.AllowedOrigin
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import swiss.dasch.Endpoints
import swiss.dasch.config.Configuration.ServiceConfig
import swiss.dasch.version.BuildInfo
import zio.*
import zio.http.*
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing

import scala.jdk.CollectionConverters.IterableHasAsJava

final class IngestApiServer(
  server: Server,
  endpoints: Endpoints,
  c: ServiceConfig,
  ctxStore: ContextStorage,
  tracing: Tracing,
) {

  private val serverOptions = ZioHttpServerOptions.customiseInterceptors
    .metricsInterceptor(ZioMetrics.default[Task]().metricsInterceptor())
    .corsInterceptor(
      CORSInterceptor.customOrThrow(CORSConfig.default.copy(allowedOrigin = AllowedOrigin.All).exposeAllHeaders),
    )
    .options

  def startup(): UIO[Unit] = for {
    _                         <- ZIO.logInfo(s"Starting ${BuildInfo.name}")
    app: Routes[Any, Response] = ZioHttpInterpreter(serverOptions).toHttp(endpoints.endpoints)
    actualPort                <- server.install(app @@ otelMiddleware)
    _                         <- ZIO.logInfo(s"Started ${BuildInfo.name}/${BuildInfo.version}, see http://${c.host}:$actualPort/docs")
  } yield ()

  private def otelMiddleware: Middleware[Any] = new Middleware[Any] {

    private val propagator: W3CTraceContextPropagator      = W3CTraceContextPropagator.getInstance()
    private val getter: TextMapGetter[Map[String, String]] = new TextMapGetter[Map[String, String]] {
      override def keys(carrier: Map[String, String]): java.lang.Iterable[String] = carrier.keys.asJava
      override def get(carrier: Map[String, String], key: String): String         = carrier.getOrElse(key, null)
    }

    override def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
      routes.transform { h =>
        Handler.scoped[Env1] {
          Handler.fromFunctionZIO { (req: Request) =>
            val headersMap =
              req.headers.toList.map(header => (header.headerName.toLowerCase, header.renderedValue)).toMap
            val extractedCtx = propagator.extract(Context.root(), headersMap, getter)
            ctxStore.set(extractedCtx) *>
              tracing.span(s"HTTP ${req.method} ${req.path}", SpanKind.SERVER)(h(req))
          }
        }
      }
  }
}

object IngestApiServer {

  def startup(): ZIO[IngestApiServer, Nothing, Unit] = ZIO.serviceWithZIO[IngestApiServer](_.startup())

  val layer: URLayer[ServiceConfig, Server] = ZLayer
    .service[ServiceConfig]
    .flatMap(cfg => Server.defaultWith(_.binding(cfg.get.host, cfg.get.port).enableRequestStreaming))
    .orDie

  val live = layer >>> ZLayer.derive[IngestApiServer]

}
