/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import sttp.model.Method.*
import sttp.monad.MonadError
import sttp.tapir.AnyEndpoint
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.DecodeSuccessContext
import sttp.tapir.server.interceptor.EndpointHandler
import sttp.tapir.server.interceptor.EndpointInterceptor
import sttp.tapir.server.interceptor.Responder
import sttp.tapir.server.interceptor.SecurityFailureContext
import sttp.tapir.server.interceptor.cors.CORSConfig
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.model.ServerResponse
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import zio.*
import zio.http.*
import zio.http.Server.Config.ResponseCompressionConfig
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing

import scala.jdk.CollectionConverters.IterableHasAsJava

import org.knora.webapi.config.KnoraApi
import org.knora.webapi.slice.api.Endpoints

final class DspApiServer(
  server: Server,
  endpoints: Endpoints,
  c: KnoraApi,
  ctxStore: ContextStorage,
  tracing: Tracing,
) {

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
      .addInterceptor(spanNameInterceptor)
      .options

  // Renames the active HTTP server span to the matched Tapir endpoint's path template
  // (e.g. "HTTP GET /admin/lists/{listIri}") once routing has succeeded. Keeps span
  // names low-cardinality per OTel HTTP semantic conventions:
  // https://opentelemetry.io/docs/specs/semconv/http/http-spans/#name
  private def spanNameInterceptor: EndpointInterceptor[Task] = new EndpointInterceptor[Task] {
    private def updateSpanName(ep: AnyEndpoint): UIO[Unit] =
      ctxStore.get.flatMap { ctx =>
        ZIO.succeed {
          val method = ep.method.map(_.method).getOrElse("HTTP")
          val path   = ep.showPathTemplate(showQueryParam = None, showQueryParamsAs = None)
          val _      = Span.fromContext(ctx).updateName(s"HTTP $method $path")
        }
      }

    override def apply[B](responder: Responder[Task, B], delegate: EndpointHandler[Task, B]): EndpointHandler[Task, B] =
      new EndpointHandler[Task, B] {
        override def onDecodeSuccess[A, U, I](ctx: DecodeSuccessContext[Task, A, U, I])(implicit
          monad: MonadError[Task],
          bodyListener: BodyListener[Task, B],
        ): Task[ServerResponse[B]] =
          updateSpanName(ctx.endpoint) *> delegate.onDecodeSuccess(ctx)

        override def onSecurityFailure[A](ctx: SecurityFailureContext[Task, A])(implicit
          monad: MonadError[Task],
          bodyListener: BodyListener[Task, B],
        ): Task[ServerResponse[B]] =
          updateSpanName(ctx.endpoint) *> delegate.onSecurityFailure(ctx)

        override def onDecodeFailure(ctx: DecodeFailureContext)(implicit
          monad: MonadError[Task],
          bodyListener: BodyListener[Task, B],
        ): Task[Option[ServerResponse[B]]] =
          delegate.onDecodeFailure(ctx)
      }
  }

  def startup(): UIO[Unit] = for {
    _                         <- ZIO.logInfo("Starting DSP API server...")
    app: Routes[Any, Response] = ZioHttpInterpreter(serverOptions).toHttp(endpoints.serverEndpoints)
    actualPort                <- Server.install(app @@ otelMiddleWare).provide(ZLayer.succeed(server))
    _                         <- ZIO.logInfo(s"API available at http://${c.externalHost}:$actualPort/version")
  } yield ()

  private def otelMiddleWare: Middleware[Any] = new Middleware[Any] {

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
              tracing.span(s"HTTP ${req.method}", SpanKind.SERVER)(h(req))
          }
        }
      }
  }
}

object DspApiServer {

  def startup: RIO[DspApiServer, Unit] = ZIO.serviceWithZIO[DspApiServer](_.startup())

  private val serverLayer = ZLayer
    .service[KnoraApi]
    .flatMap(cfg =>
      val host = cfg.get.internalHost
      val port = cfg.get.internalPort
      ZLayer.fromZIO(ZIO.logInfo(s"Binding DSP API server to $host:$port")) >>>
        Server
          .defaultWith(
            _.binding(cfg.get.internalHost, cfg.get.internalPort).enableRequestStreaming
              .responseCompression(ResponseCompressionConfig.default),
          ),
    )
    .orDie

  val layer = serverLayer >>> ZLayer.derive[DspApiServer]
}
