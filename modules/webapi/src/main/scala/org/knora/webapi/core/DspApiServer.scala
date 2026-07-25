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
import sttp.model.Header
import sttp.model.HeaderNames
import sttp.model.Method.*
import sttp.monad.MonadError
import sttp.tapir.AnyEndpoint
import sttp.tapir.DecodeResult
import sttp.tapir.model.ServerRequest
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

  // Both the interceptor chain and the tracing middleware live on the companion so a spec can run requests through
  // exactly what this server runs -- the passthrough's Accept exemption, its audit entries, its Connection handling
  // and the trace/span annotations those entries carry are all behaviour of these two, and testing a re-created
  // copy would test the copy.
  private val serverOptions: ZioHttpServerOptions[Any] = DspApiServer.serverOptions(ctxStore)

  def startup(): UIO[Unit] = for {
    _                         <- ZIO.logInfo("Starting DSP API server...")
    app: Routes[Any, Response] = ZioHttpInterpreter(serverOptions).toHttp(endpoints.serverEndpoints)
    actualPort                <-
      Server.install(app @@ DspApiServer.otelMiddleWare(ctxStore, tracing)).provide(ZLayer.succeed(server))
    _ <- ZIO.logInfo(s"API available at http://${c.externalHost}:$actualPort/version")
  } yield ()
}

object DspApiServer {

  def startup: RIO[DspApiServer, Unit] = ZIO.serviceWithZIO[DspApiServer](_.startup())

  /**
   * The server's tracing middleware, as a function rather than a field, for the same reason as [[serverOptions]]: it
   * opens the SERVER span every request runs inside, and it is what annotates that request's log entries with
   * `trace_id` and `span_id`. Those annotations are the log-to-trace jump an operator uses, so a spec that wants to
   * pin them has to run the real middleware rather than assert on a rebuilt one.
   */
  private[core] def otelMiddleWare(ctxStore: ContextStorage, tracing: Tracing): Middleware[Any] = new Middleware[Any] {

    private val propagator: W3CTraceContextPropagator      = W3CTraceContextPropagator.getInstance()
    private val getter: TextMapGetter[Map[String, String]] = new TextMapGetter[Map[String, String]] {
      override def keys(carrier: Map[String, String]): java.lang.Iterable[String] = carrier.keys.asJava
      override def get(carrier: Map[String, String], key: String): String         = carrier.getOrElse(key, null)
    }

    // Map 5xx responses to span status ERROR per OTel HTTP semconv. 2xx/3xx/4xx stay UNSET
    // (4xx are client errors and must not be surfaced as server-side failures).
    //
    // Nearly everything reaches this mapper as a *response*, failures included: ztapir's `zServerLogic` /
    // `zServerSecurityLogic` wrap the logic in `.either.resurrect`, so even a defect becomes a typed failure
    // before the interpreter sees it, is answered as a 500 by tapir's exception handler, and is matched here
    // -- ERROR, with no description.
    //
    // What is left in the routes' error channel reaches zio-telemetry's own default instead, which writes
    // `Cause.prettyPrint` into the span status description. Two shapes get there:
    //
    //   - `Cause.Fail(Response)`. `ZioHttpInterpreter.toHttp` folds *every* cause into
    //     `ZIO.fail(Response.internalServerError(...))`, so this is not only the non-`NonFatal` throwable that
    //     escapes ztapir's `resurrect`: any defect raised *outside* the ztapir-wrapped logic lands here too --
    //     the interceptor hooks below (`updateSpanMetadata`, `logRejectedPassthroughDecode`), request-body
    //     reading, response building. `Response` is a case class, so `prettyPrint` renders its body.
    //   - `Cause.Interrupt`, with no failure value at all: ZIO re-raises an external interrupt after tapir's
    //     `foldCauseZIO` has handled it, so an abandoned request can reach the middleware as a bare interrupt.
    //
    // Neither rendering is caller data. The body is the constant `"Request interrupted"` or
    // `cause.squash.getMessage` of a defect from one of the classes above -- none of which is an application
    // error path holding caller-supplied text -- and an interrupt cause carries only fiber ids and a ZIO trace.
    //
    // This stays a Success mapper by choice, not because a failure mapping would not work. zio-telemetry writes
    // `cause.prettyPrint` only when the *mapped* status is ERROR, so composing in
    // `StatusMapper.Failure[Response](_ => Result(StatusCode.UNSET))` -- the same `unsetOnFailure` remedy
    // `SanitizedSpan` relies on -- would suppress the description on the first shape. It would not reach the
    // second, which has no failure value for the mapper to match, and there is no way to keep ERROR *and* drop
    // the description: the mapper's only lever is the status code. That trade costs every escaped-defect 500 in
    // the API its error span, which is worth more than suppressing a description that is not caller data.
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

  /**
   * The server's interceptor chain, as a value rather than a field, so a spec can interpret a set of endpoints
   * through the exact chain production runs. Three of this branch's behaviours -- the passthrough's exemption from
   * Accept negotiation, its decode-failure audit entries, and marking a rejection as the last response on its
   * connection -- live only here, and a spec that rebuilt an equivalent chain would be asserting on its own copy.
   */
  private[core] def serverOptions(ctxStore: ContextStorage): ZioHttpServerOptions[Any] =
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
      .addInterceptor(spanNameInterceptor(ctxStore))
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
  private def spanNameInterceptor(ctxStore: ContextStorage): EndpointInterceptor[Task] = new EndpointInterceptor[Task] {

    /**
     * Attributes a passthrough request the framework refused while decoding it: an over-cap request body, an
     * unsupported `Content-Type`, a form body with no `query` field. None of these reach the rest service either, so
     * without this they would be the one class of call the surface does not account for. The caller is authenticated
     * and authorized by this point -- decoding runs after the security logic -- but the principal is not carried into
     * this hook, so the entry has no identity.
     *
     * Only ever called for a decode failure the delegate actually *answered*. A decode failure that returns `None`
     * is not a rejected call at all: tapir groups endpoints by path template and tries each with `Method.ANY`, so an
     * anonymous `GET /admin/sparql/query` is routed into this endpoint's group, fails the method decode, returns
     * `None` and falls through to a `404`. Logging before consulting the delegate invented a
     * `malformed-request user_iri=-` entry for every such request -- one that never was a call, from a caller who
     * never authenticated -- which both contradicts "exactly one entry per call" and hands an unauthenticated
     * stranger an amplifier for the surface's own audit channel.
     */
    private def logRejectedPassthroughDecode(ctx: DecodeFailureContext): UIO[Unit] = {
      val outcome = ctx.failure match {
        case DecodeResult.Error(_, StreamMaxLengthExceededException(_)) => "request-cap-exceeded"
        case _                                                          => "malformed-request"
      }
      SparqlPassthroughAudit(outcome).log.when(SparqlPassthroughEndpoints.isPassthroughRoute(ctx.endpoint)).unit
    }

    /**
     * Marks a passthrough response produced *before* the request body was read as the last one on its connection.
     *
     * Several rejections on this route answer without consuming the body: the security logic's `401`/`403`, which
     * runs before body decoding by design, and a `415`, where no body variant matched the request's `Content-Type`.
     * Bodies declaring a length above the server's aggregation threshold are streamed, and zio-http leaves such a
     * connection with
     * `autoRead=false` when the handler answers without consuming it -- the next keep-alive request on that connection
     * is then silently dropped and the client hangs until its own timeout. `Connection: close` takes the connection out
     * of the client's pool instead, which is the same remedy tapir applies to its own over-cap `413`; the header is
     * added only once, since that `413` already carries it.
     *
     * It is applied only when the body plausibly went unread, which is a property of the *request*, not of the
     * rejection: see [[bodyPlausiblyUnread]]. Closing on every rejection also killed healthy connections -- a missing
     * `query` form field or an unsupported `Content-Type` on a small body is answered after zio-http has already read
     * the whole thing, so there is nothing left in the channel and nothing to fix.
     */
    private def closeConnectionIfPassthrough[B](
      ep: AnyEndpoint,
      request: ServerRequest,
      response: ServerResponse[B],
    ): ServerResponse[B] =
      if (
        SparqlPassthroughEndpoints.isPassthroughRoute(ep) && bodyPlausiblyUnread(request) &&
        !response.headers.exists(closesConnection)
      )
        response.addHeaders(Seq(Header(HeaderNames.Connection, "close")))
      else response

    /**
     * True when this request's body may still be sitting unread in the channel.
     *
     * The server runs `RequestStreaming.Hybrid(maxAggregatedRequestBodySize)`, and zio-http's
     * `HybridContentLengthHandler` decides from the *declared* `Content-Length` alone: only a value above the
     * threshold swaps Netty's `HttpObjectAggregator` out for streaming. Everything else keeps the aggregator, which
     * reads the whole body before any handler runs. An absent `Content-Length` -- a chunked body -- reads there as
     * `-1`, which is not above the threshold, so it takes the aggregating branch and is fully read as well. (A
     * chunked body that then exceeds the aggregator's own `maxContentLength` never reaches this API at all: Netty
     * answers it with a bare `413` below us.)
     *
     * A declared `Content-Length` above the threshold is therefore the only case where bytes can still be in the
     * channel. Counting an absent one as unread instead was wrong in the expensive direction: it closed connections
     * that were fine, and gave an unauthenticated caller one upstream-connection churn per `POST` sent with
     * `Transfer-Encoding: chunked` -- an empty body being enough.
     */
    private def bodyPlausiblyUnread(request: ServerRequest): Boolean =
      request.contentLength.exists(_ > DspApiServer.maxAggregatedRequestBodySize)

    private def closesConnection(header: Header): Boolean =
      header.name.equalsIgnoreCase(HeaderNames.Connection) && header.value.equalsIgnoreCase("close")

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
          // The passthrough's own security-logic rejections (401 and 403) are attributed there, where the identity
          // behind a 403 is still in scope. Attributing them here as well would double-count them, and this hook
          // cannot tell the two apart.
          updateSpanMetadata(ctx.endpoint) *>
            delegate.onSecurityFailure(ctx).map(closeConnectionIfPassthrough(ctx.endpoint, ctx.request, _))

        // The delegate is consulted first and the entry written only if it answered. `None` means this endpoint
        // did not claim the request at all -- see logRejectedPassthroughDecode.
        //
        // Uninterruptible from the delegate's answer onwards: the request has been rejected by the time we get here,
        // so an interrupt landing between the answer and the write would drop the entry for a call that *was*
        // answered -- the one way this hook could still break "exactly one entry per call". The rest service's own
        // report is on an `onExit` finalizer, which is uninterruptible for the same reason.
        override def onDecodeFailure(ctx: DecodeFailureContext)(implicit
          monad: MonadError[Task],
          bodyListener: BodyListener[Task, B],
        ): Task[Option[ServerResponse[B]]] =
          delegate.onDecodeFailure(ctx).flatMap {
            case Some(response) =>
              logRejectedPassthroughDecode(ctx)
                .as(Some(closeConnectionIfPassthrough(ctx.endpoint, ctx.request, response)))
                .uninterruptible
            case None => ZIO.none
          }
      }
  }

  // With fully streamed request bodies (RequestStreaming.Enabled), zio-http (<= 3.11.3) leaves the
  // connection with autoRead=false when a handler responds without consuming the body — e.g. a
  // tapir security failure (401/403), an unmatched route, or an endpoint that declares no body
  // input. If the body bytes arrive after the initial read burst, the channel never reads again and
  // the next keep-alive request on it is silently dropped (the client times out). Hybrid mode
  // aggregates bodies up to this size so they are always fully read, and streams only larger ones
  // (the project-import zip upload); this was the root cause of the recurring e2e CI timeouts.
  //
  // Three consequences worth stating, because they are easy to get wrong. Aggregation happens before any
  // tapir logic runs — before the security logic, let alone body decoding — so a body up to this size is
  // buffered for *any* caller, authenticated or not. The switch is made on the *declared* Content-Length
  // alone, so a chunked body is aggregated too — and one that then outgrows this size is answered by Netty
  // with a bare 413 that never reaches tapir. And the passthrough's own `Connection: close` handling keys
  // off this threshold (see `bodyPlausiblyUnread`): only a declared length above it can leave a body unread.
  private[core] val maxAggregatedRequestBodySize: Int = 1024 * 1024

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
