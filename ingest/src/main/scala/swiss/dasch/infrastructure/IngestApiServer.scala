/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import sttp.monad.MonadError
import sttp.tapir.AnyEndpoint
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.DecodeSuccessContext
import sttp.tapir.server.interceptor.EndpointHandler
import sttp.tapir.server.interceptor.EndpointInterceptor
import sttp.tapir.server.interceptor.Responder
import sttp.tapir.server.interceptor.SecurityFailureContext
import sttp.tapir.server.interceptor.cors.CORSConfig
import sttp.tapir.server.interceptor.cors.CORSConfig.AllowedOrigin
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.interpreter.BodyListener
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.model.ServerResponse
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
    .addInterceptor(spanNameInterceptor)
    .options

  // Renames the active HTTP server span to the matched Tapir endpoint's path template
  // (e.g. "HTTP GET /projects/{shortcode}") once routing has succeeded, and records
  // http.route. Keeps span names low-cardinality per OTel HTTP semantic conventions:
  // https://opentelemetry.io/docs/specs/semconv/http/http-spans/#name
  private def spanNameInterceptor: EndpointInterceptor[Task] = new EndpointInterceptor[Task] {
    private def updateSpanMetadata(ep: AnyEndpoint): UIO[Unit] =
      ctxStore.get.flatMap { ctx =>
        ZIO.succeed {
          val method = ep.method.map(_.method).getOrElse("HTTP")
          val route  = ep.showPathTemplate(showQueryParam = None, showQueryParamsAs = None)
          val span   = Span.fromContext(ctx)
          val _      = span.updateName(s"HTTP $method $route")
          val _      = span.setAttribute(HttpAttributes.HTTP_ROUTE, route)
        }
      }

    override def apply[B](responder: Responder[Task, B], delegate: EndpointHandler[Task, B]): EndpointHandler[Task, B] =
      new EndpointHandler[Task, B] {
        override def onDecodeSuccess[A, U, I](ctx: DecodeSuccessContext[Task, A, U, I])(implicit
          monad: MonadError[Task],
          bodyListener: BodyListener[Task, B],
        ): Task[ServerResponse[B]] =
          updateSpanMetadata(ctx.endpoint) *> delegate.onDecodeSuccess(ctx)

        override def onSecurityFailure[A](ctx: SecurityFailureContext[Task, A])(implicit
          monad: MonadError[Task],
          bodyListener: BodyListener[Task, B],
        ): Task[ServerResponse[B]] =
          updateSpanMetadata(ctx.endpoint) *> delegate.onSecurityFailure(ctx)

        override def onDecodeFailure(ctx: DecodeFailureContext)(implicit
          monad: MonadError[Task],
          bodyListener: BodyListener[Task, B],
        ): Task[Option[ServerResponse[B]]] =
          delegate.onDecodeFailure(ctx)
      }
  }

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
              tracing.span(s"HTTP ${req.method}", SpanKind.SERVER) {
                for {
                  _    <- tracing.setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, req.method.toString)
                  _    <- tracing.setAttribute(UrlAttributes.URL_PATH, req.path.toString)
                  resp <- h(req)
                  _    <- tracing.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, resp.status.code.toLong)
                } yield resp
              }
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
