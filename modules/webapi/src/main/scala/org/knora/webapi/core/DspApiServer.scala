/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.opentelemetry.semconv.UserAgentAttributes
import sttp.capabilities.StreamMaxLengthExceededException
import sttp.model.Method.*
import sttp.monad.MonadError
import sttp.tapir.AnyEndpoint
import sttp.tapir.DecodeResult
import sttp.tapir.server.interceptor.DecodeFailureContext
import sttp.tapir.server.interceptor.DecodeSuccessContext
import sttp.tapir.server.interceptor.EndpointHandler
import sttp.tapir.server.interceptor.EndpointInterceptor
import sttp.tapir.server.interceptor.Responder
import sttp.tapir.server.interceptor.SecurityFailureContext
import sttp.tapir.server.interceptor.content.NotAcceptableInterceptor
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
import zio.http.Server.RequestStreaming
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.StatusMapper
import zio.telemetry.opentelemetry.tracing.Tracing

import scala.jdk.CollectionConverters.IterableHasAsJava

import org.knora.webapi.config.KnoraApi
import org.knora.webapi.slice.api.Endpoints
import org.knora.webapi.slice.api.admin.SparqlPassthroughAudit
import org.knora.webapi.slice.api.admin.SparqlPassthroughEndpoints

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
      .notAcceptableInterceptor(new PassthroughAwareNotAcceptableInterceptor)
      .addInterceptor(spanNameInterceptor)
      .options

  /**
   * Tapir's own `Accept` negotiation, with the admin SPARQL passthrough exempted.
   *
   * By default tapir answers `406` with an empty body when none of an endpoint's declared output media types matches
   * the request's `Accept`, before the server logic runs. For every other endpoint that is what we want. For the
   * passthrough it is wrong twice over: content negotiation there is the *store's* job, so a caller could otherwise
   * never ask for anything but the one declared type, and the store's own `406` -- which that surface is required to
   * relay verbatim -- would be unreachable behind tapir's empty one.
   *
   * Declaring the passthrough's body as `*&#47;*` does not help: the matching treats a wildcard as meaningful only on
   * the requested-range side, so a supported `*&#47;*` matches no concrete `Accept`. Exempting the one route is the
   * targeted fix; disabling the interceptor globally would silently change every other endpoint's behaviour.
   */
  private final class PassthroughAwareNotAcceptableInterceptor extends NotAcceptableInterceptor[Task] {
    override def apply[B](
      responder: Responder[Task, B],
      endpointHandler: EndpointHandler[Task, B],
    ): EndpointHandler[Task, B] = {
      val negotiating = super.apply(responder, endpointHandler)
      new EndpointHandler[Task, B] {
        override def onDecodeSuccess[A, U, I](ctx: DecodeSuccessContext[Task, A, U, I])(implicit
          monad: MonadError[Task],
          bodyListener: BodyListener[Task, B],
        ): Task[ServerResponse[B]] =
          if (leavesNegotiationToTheStore(ctx.endpoint)) endpointHandler.onDecodeSuccess(ctx)
          else negotiating.onDecodeSuccess(ctx)

        // These two are pass-throughs in the interceptor being wrapped, so delegating past it would be
        // behaviour-identical today. They go through it anyway: the exemption above is meant to be the only
        // difference from stock negotiation, and skipping the wrapped handler on the other two hooks would make a
        // future change to either silently not apply to this server.
        override def onSecurityFailure[A](ctx: SecurityFailureContext[Task, A])(implicit
          monad: MonadError[Task],
          bodyListener: BodyListener[Task, B],
        ): Task[ServerResponse[B]] = negotiating.onSecurityFailure(ctx)

        override def onDecodeFailure(ctx: DecodeFailureContext)(implicit
          monad: MonadError[Task],
          bodyListener: BodyListener[Task, B],
        ): Task[Option[ServerResponse[B]]] = negotiating.onDecodeFailure(ctx)
      }
    }

    private def leavesNegotiationToTheStore(ep: AnyEndpoint): Boolean =
      SparqlPassthroughEndpoints.isPassthroughRoute(ep)
  }

  // Renames the active HTTP server span to the matched Tapir endpoint's path template
  // (e.g. "HTTP GET /admin/lists/{listIri}") once routing has succeeded, and records
  // http.route. Keeps span names low-cardinality per OTel HTTP semantic conventions:
  // https://opentelemetry.io/docs/specs/semconv/http/http-spans/#name
  private def spanNameInterceptor: EndpointInterceptor[Task] = new EndpointInterceptor[Task] {

    /**
     * Records an unauthenticated attempt on the admin SPARQL passthrough. That surface must attribute every call
     * including rejected ones, and a request rejected by the endpoint's security logic never reaches the service that
     * does the logging -- security logic runs first -- so this hook is the only place the rejection is observable.
     *
     * HARD PROHIBITION: `ctx.securityInput` holds the raw bearer token and `ctx.request` exposes the `Authorization`
     * header. Neither may be read here. Writing either would put a live credential into stdout and onward into the
     * log backend. Only the outcome, method and route template are recorded; `trace_id` is attached by the
     * surrounding middleware's log annotation.
     */
    private def logRejectedPassthrough(ep: AnyEndpoint): UIO[Unit] =
      SparqlPassthroughAudit("unauthenticated").log.when(SparqlPassthroughEndpoints.isPassthroughRoute(ep)).unit

    /**
     * Attributes a passthrough request the framework refused while decoding it: an over-cap request body, an
     * unsupported `Content-Type`, a form body with no `query` field. None of these reach the rest service either, so
     * without this they would be the one class of call the surface does not account for. The caller is authenticated
     * and authorized by this point -- decoding runs after the security logic -- but the principal is not carried into
     * this hook, so the entry has no identity.
     */
    private def logRejectedPassthroughDecode(ctx: DecodeFailureContext): UIO[Unit] = {
      val outcome = ctx.failure match {
        case DecodeResult.Error(_, StreamMaxLengthExceededException(_)) => "request-cap-exceeded"
        case _                                                          => "malformed-request"
      }
      SparqlPassthroughAudit(outcome).log.when(SparqlPassthroughEndpoints.isPassthroughRoute(ctx.endpoint)).unit
    }

    private def updateSpanMetadata(ep: AnyEndpoint): UIO[Unit] =
      ctxStore.get.flatMap { ctx =>
        ZIO.succeed {
          val method = ep.method.map(_.method).getOrElse("HTTP")
          val route  = ep.showPathTemplate(showQueryParam = None, showQueryParamsAs = None)
          val span   = Span.fromContext(ctx)
          val _      = span.updateName(s"$method $route")
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
          updateSpanMetadata(ctx.endpoint) *> logRejectedPassthrough(ctx.endpoint) *>
            delegate.onSecurityFailure(ctx)

        override def onDecodeFailure(ctx: DecodeFailureContext)(implicit
          monad: MonadError[Task],
          bodyListener: BodyListener[Task, B],
        ): Task[Option[ServerResponse[B]]] =
          logRejectedPassthroughDecode(ctx) *> delegate.onDecodeFailure(ctx)
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

    // Map 5xx responses to span status ERROR per OTel HTTP semconv. 2xx/3xx/4xx stay UNSET
    // (4xx are client errors and must not be surfaced as server-side failures). Typed
    // failures and defects are handled by zio-telemetry's default and produce ERROR
    // automatically, with `Cause.prettyPrint` captured as the status description.
    private val httpStatusMapper: StatusMapper.Success[Response] =
      StatusMapper.Success[Response] {
        case resp if resp.status.code >= 500 => StatusMapper.Result(StatusCode.ERROR)
      }

    override def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
      routes.transform { h =>
        Handler.scoped[Env1] {
          Handler.fromFunctionZIO { (req: Request) =>
            val headersMap =
              req.headers.toList.map(header => (header.headerName.toLowerCase, header.renderedValue)).toMap
            val extractedCtx = propagator.extract(Context.root(), headersMap, getter)
            ctxStore.set(extractedCtx) *>
              tracing.span(s"${req.method}", SpanKind.SERVER, statusMapper = httpStatusMapper) {
                ctxStore.get.flatMap { ctx =>
                  val body = for {
                    _ <- tracing.setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, req.method.toString)
                    _ <- tracing.setAttribute(UrlAttributes.URL_PATH, req.path.toString)
                    _ <- ZIO.foreachDiscard(req.headers.get("User-Agent"))(ua =>
                           tracing.setAttribute(UserAgentAttributes.USER_AGENT_ORIGINAL, ua),
                         )
                    resp <- h(req).onExit {
                              case Exit.Success(resp) =>
                                tracing.setAttribute(
                                  HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                                  resp.status.code.toLong,
                                )
                              case _ => ZIO.unit
                            }
                  } yield resp

                  val sc = Span.fromContext(ctx).getSpanContext
                  if (sc.isValid)
                    body @@ ZIOAspect.annotated("trace_id", sc.getTraceId)
                      @@ ZIOAspect.annotated("span_id", sc.getSpanId)
                  else
                    body
                }
              }
          }
        }
      }
  }
}

object DspApiServer {

  def startup: RIO[DspApiServer, Unit] = ZIO.serviceWithZIO[DspApiServer](_.startup())

  // With fully streamed request bodies (RequestStreaming.Enabled), zio-http (<= 3.11.3) leaves the
  // connection with autoRead=false when a handler responds without consuming the body — e.g. a
  // tapir security failure (401/403), an unmatched route, or an endpoint that declares no body
  // input. If the body bytes arrive after the initial read burst, the channel never reads again and
  // the next keep-alive request on it is silently dropped (the client times out). Hybrid mode
  // aggregates bodies up to this size so they are always fully read, and streams only larger ones
  // (the project-import zip upload); this was the root cause of the recurring e2e CI timeouts.
  private val maxAggregatedRequestBodySize: Int = 1024 * 1024

  private val serverLayer = ZLayer
    .service[KnoraApi]
    .flatMap(cfg =>
      val host = cfg.get.internalHost
      val port = cfg.get.internalPort
      ZLayer.fromZIO(ZIO.logInfo(s"Binding DSP API server to $host:$port")) >>>
        Server
          .defaultWith(
            _.binding(cfg.get.internalHost, cfg.get.internalPort)
              .requestStreaming(RequestStreaming.Hybrid(maxAggregatedRequestBodySize))
              .responseCompression(ResponseCompressionConfig.default),
          ),
    )
    .orDie

  val layer = serverLayer >>> ZLayer.derive[DspApiServer]
}
