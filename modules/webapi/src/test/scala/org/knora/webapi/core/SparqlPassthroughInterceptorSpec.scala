/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.trace.ReadableSpan
import org.junit.runner.RunWith
import sttp.model.StatusCode as SttpStatusCode
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.*
import zio.http.*
import zio.telemetry.opentelemetry.context.ContextStorage
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test.*

import java.nio.charset.StandardCharsets

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo.builtIn.SystemProject
import org.knora.webapi.slice.api.admin.SparqlPassthroughEndpoints
import org.knora.webapi.slice.api.admin.SparqlPassthroughServerEndpoints
import org.knora.webapi.slice.api.admin.service.SparqlPassthroughRestService
import org.knora.webapi.slice.api.admin.service.SparqlPassthroughTestEnv
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.RawSparqlResponse

/**
 * What the passthrough's *boundary* rejections put in the audit log, asserted as entries rather than as status codes.
 *
 * The outcomes here -- `unauthenticated`, the `forbidden` from the security logic, and `malformed-request` -- are the
 * ones the rest service never sees, so `SparqlPassthroughAuditSpec` cannot reach them. They were previously claimed
 * to be covered over HTTP by `SparqlPassthroughE2ESpec`, which only ever asserted their status codes: nothing pinned
 * the entries, which is how a decode-failure entry came to be written for requests that were never calls at all.
 *
 * The endpoints are interpreted through [[DspApiServer.serverOptions]], the same interceptor chain the running server
 * installs, and requests are run against the resulting `Routes` in memory. That is what makes these assertions about
 * the production hooks rather than about a re-created approximation of them -- and it keeps the audit lines inside
 * the test's own fiber, where `ZTestLogger` can see them. Over real HTTP they would not be visible at all: the server
 * captures its loggers when its layer is built, before any individual test's logger exists.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class SparqlPassthroughInterceptorSpec extends ZIOSpecDefault {

  private val path  = "/admin/sparql/query"
  private val token = "a-valid-token"

  private val normalUser =
    User(
      "http://rdfh.ch/users/sparql-passthrough-interceptor",
      "username",
      "email@example.com",
      "given name",
      "family name",
      status = true,
      "lang",
    )

  private val systemAdmin = normalUser.copy(permissions =
    PermissionsDataADM(Map(SystemProject.id.value -> List(KnoraGroupRepo.builtIn.SystemAdmin.id.value))),
  )

  private val selectQuery = "SELECT ?s WHERE { ?s ?p ?o } LIMIT 1"

  private val storeAnswersOk = SparqlPassthroughTestEnv.stubStore(_ =>
    ZIO.succeed(
      RawSparqlResponse(
        SttpStatusCode.Ok,
        Some("application/sparql-results+json"),
        "{}".getBytes(StandardCharsets.UTF_8),
      ),
    ),
  )

  private def env(user: User) = envWith(SparqlPassthroughTestEnv.tokenAuthenticator(token, user))

  /**
   * No second `ContextStorage` is appended here, and that is load-bearing. The test env exports the very
   * `Tracing`/`ContextStorage` pair its services hold; appending `OpenTelemetry.contextZIO` alongside it handed the
   * interceptor a *different* storage from the one `Tracing` writes spans into, so `updateSpanMetadata` renamed a
   * span nobody could see and the span assertions below would have passed against an empty context.
   */
  private def envWith(authenticator: Authenticator, overrides: (String, Any)*) =
    SparqlPassthroughTestEnv.layerWithAuthenticator(storeAnswersOk, authenticator, overrides*)

  private val routes =
    for {
      appConfig   <- ZIO.service[AppConfig]
      endpoints   <- ZIO.service[SparqlPassthroughEndpoints]
      restService <- ZIO.service[SparqlPassthroughRestService]
      ctxStore    <- ZIO.service[ContextStorage]
    } yield ZioHttpInterpreter(DspApiServer.serverOptions(ctxStore))
      .toHttp(new SparqlPassthroughServerEndpoints(appConfig, endpoints, restService).serverEndpoints)

  private val auditEntries: UIO[Chunk[ZTestLogger.LogEntry]] =
    ZTestLogger.logOutput.map(_.filter(_.message().startsWith("SPARQL passthrough:")))

  /** Runs one request through the real interceptor chain, returning the response and the entries it produced. */
  private def run(request: Request) =
    ZIO.scoped {
      for {
        app      <- routes
        response <- app.runZIO(request)
        entries  <- auditEntries.map(_.map(_.message()))
      } yield (response, entries)
    }

  /** As [[run]], but with the server's tracing middleware in front, so entries carry the trace annotations. */
  private def runTraced(request: Request) =
    ZIO.scoped {
      for {
        app      <- routes
        ctxStore <- ZIO.service[ContextStorage]
        tracing  <- ZIO.service[Tracing]
        _        <- (app @@ DspApiServer.otelMiddleWare(ctxStore, tracing)).runZIO(request)
        entries  <- auditEntries
      } yield entries
    }

  /**
   * Runs one request inside a SERVER span opened on the same `Tracing` the interceptor reads, and returns the name
   * and `http.route` the interceptor left on it. Stands in for the tracing middleware, which opens exactly such a
   * span; reading them back needs the span still open, which is why they are read here rather than after the fact.
   */
  private def runInSpan(request: Request) =
    ZIO.scoped {
      for {
        app     <- routes
        tracing <- ZIO.service[Tracing]
        named   <- tracing.span("POST", SpanKind.SERVER) {
                   app.runZIO(request) *> tracing.getCurrentSpanUnsafe.map {
                     case readable: ReadableSpan =>
                       (readable.getName, Option(readable.toSpanData.getAttributes.get(httpRoute)))
                     case _ => ("", None)
                   }
                 }
      } yield named
    }

  private val httpRoute = AttributeKey.stringKey("http.route")

  private def outcomeOf(entry: String): String =
    entry.split(' ').collectFirst { case kv if kv.startsWith("outcome=") => kv.drop("outcome=".length) }.getOrElse("")

  private def url(p: String): URL = URL.decode(p).getOrElse(throw new IllegalArgumentException(p))

  /** A well-formed request: a real client's `Content-Length`, which an in-memory `Request` does not get for free. */
  private def post(body: String, contentType: String, bearer: Option[String]): Request =
    postDeclaring(Some(body.length.toLong))(body, contentType, bearer)

  /**
   * `contentLength` is load-bearing rather than incidental: whether a rejection closes the connection is decided from
   * the *declared* length, because that is the only thing zio-http's hybrid streaming switches on. `None` models a
   * chunked request, and a value above the aggregation threshold the one case where the body may really be unread.
   */
  private def postDeclaring(
    contentLength: Option[Long],
  )(body: String, contentType: String, bearer: Option[String]): Request = {
    val base = Request
      .post(url(path), Body.fromString(body))
      .addHeader(Header.Custom("Content-Type", contentType))
    val sized = contentLength.fold(base)(length => base.addHeader(Header.ContentLength(length)))
    bearer.fold(sized)(t => sized.addHeader(Header.Custom("Authorization", s"Bearer $t")))
  }

  /** A declared length above the server's aggregation threshold, i.e. a body zio-http would have streamed. */
  private val aboveAggregationThreshold = Some(DspApiServer.maxAggregatedRequestBodySize.toLong + 1)

  private def headerValue(response: Response, name: String): Option[String] =
    response.headers.collectFirst { case h if h.headerName.equalsIgnoreCase(name) => h.renderedValue }

  val spec: Spec[Any, Any] = suite("the SPARQL passthrough's server-side hooks")(
    test("a request the route does not serve is a 404 and produces no entry at all") {
      // The finding this pins. Tapir groups endpoints by path template and matches them with `Method.ANY`, so a GET
      // on this path is routed into the passthrough's group, fails the method decode, and the handler answers
      // `None` -- the request falls through to a 404 and was never a call on this surface. Logging before consulting
      // the delegate invented a `malformed-request user_iri=-` entry for it: an entry with no call behind it, and
      // one any unauthenticated stranger could produce on demand.
      run(Request.get(url(path)))
        .map((response, entries) => assertTrue(response.status == Status.NotFound, entries.isEmpty))
        .provide(env(systemAdmin))
    },
    test("an unauthenticated POST is a 401 with exactly one unauthenticated entry") {
      run(post(selectQuery, "application/sparql-query", None))
        .map((response, entries) =>
          assertTrue(
            response.status == Status.Unauthorized,
            entries.size == 1,
            outcomeOf(entries.head) == "unauthenticated",
          ),
        )
        .provide(env(systemAdmin))
    },
    test("an authenticated non-SystemAdmin POST is a 403 with exactly one forbidden entry carrying the identity") {
      run(post(selectQuery, "application/sparql-query", Some(token)))
        .map((response, entries) =>
          assertTrue(
            response.status == Status.Forbidden,
            entries.size == 1,
            outcomeOf(entries.head) == "forbidden",
            // A 403 is the one rejection where the caller is known, which is what makes the entry an attribution.
            entries.head.contains(s"user_iri=${normalUser.id}"),
          ),
        )
        .provide(env(normalUser))
    },
    test("a body the framework cannot decode is a 415 with exactly one malformed-request entry") {
      run(post(selectQuery, "text/plain", Some(token)))
        .map((response, entries) =>
          assertTrue(
            response.status == Status.UnsupportedMediaType,
            entries.size == 1,
            outcomeOf(entries.head) == "malformed-request",
          ),
        )
        .provide(env(systemAdmin))
    },
    test("a body over the endpoint's request cap is attributed as request-cap-exceeded, not malformed-request") {
      // The `request-cap-exceeded` branch of the decode hook was the one outcome nothing pinned. The e2e spec
      // asserts only the 413, which tapir's own decode-failure handler produces whether or not our hook recognised
      // the failure -- so a tapir change reshaping `DecodeResult.Error(_, StreamMaxLengthExceededException(_))`
      // would silently degrade the entry to `malformed-request` with every test still green. The audit spec covers
      // the *rest service's* own cap, a different path: this one never reaches the server logic at all.
      run(post(selectQuery, "application/sparql-query", Some(token)))
        .map((response, entries) =>
          assertTrue(
            response.status == Status.RequestEntityTooLarge,
            entries.size == 1,
            outcomeOf(entries.head) == "request-cap-exceeded",
          ),
        )
        .provide(
          envWith(
            SparqlPassthroughTestEnv.tokenAuthenticator(token, systemAdmin),
            "app.triplestore.sparql-passthrough.max-request-body-bytes" -> 10,
          ),
        )
    },
    test("a form body with no query field is a decode failure, and is attributed as one") {
      run(post("notquery=x", "application/x-www-form-urlencoded", Some(token)))
        .map((response, entries) =>
          assertTrue(
            response.status.code >= 400,
            entries.size == 1,
            outcomeOf(entries.head) == "malformed-request",
          ),
        )
        .provide(env(systemAdmin))
    },
    test("an accepted call is attributed by the rest service, and the boundary hooks stay out of it") {
      run(post(selectQuery, "application/sparql-query", Some(token)))
        .map((response, entries) =>
          assertTrue(
            response.status == Status.Ok,
            entries.size == 1,
            outcomeOf(entries.head) == "ok",
            // The relayed Content-Type is the store's, and the response says not to second-guess it.
            headerValue(response, "Content-Type").contains("application/sparql-results+json"),
            headerValue(response, "X-Content-Type-Options").contains("nosniff"),
          ),
        )
        .provide(env(systemAdmin))
    },
    test("a rejection on a body small enough to have been read does not close the connection") {
      // `Connection: close` is the remedy for a body left unread in the channel, which cannot have happened here:
      // the server aggregates anything up to its threshold before tapir runs at all. Sending it anyway took a
      // perfectly usable connection out of the client's pool on every 401, 403 and 415.
      run(post(selectQuery, "text/plain", Some(token)))
        .map((response, _) => assertTrue(headerValue(response, "Connection").isEmpty))
        .provide(env(systemAdmin))
    },
    test("a rejection on a chunked body does not close it either, because that body was aggregated too") {
      // The finding this pins. zio-http's hybrid streaming switches on the *declared* Content-Length alone: an
      // absent one reads as -1, which is not above the threshold, so Netty's aggregator stays installed and the
      // chunked body is fully read before tapir runs. Treating "no Content-Length" as unread closed healthy
      // connections, and let an unauthenticated caller force one upstream-connection churn per chunked POST.
      run(postDeclaring(None)(selectQuery, "text/plain", Some(token)))
        .map((response, _) => assertTrue(headerValue(response, "Connection").isEmpty))
        .provide(env(systemAdmin))
    },
    test("a rejection on a body declared above the aggregation threshold does close it") {
      // The case the header actually exists for, and the only one left: above the threshold zio-http streams the
      // body, so a rejection that answers without consuming it leaves bytes in the channel.
      run(postDeclaring(aboveAggregationThreshold)(selectQuery, "text/plain", Some(token)))
        .map((response, _) => assertTrue(headerValue(response, "Connection").contains("close")))
        .provide(env(systemAdmin))
    },
    test("a defect in the security logic is answered through the same hook, and is attributed as one entry") {
      // Two findings in one. A defect used to escape the security logic entirely and be answered by tapir's outer
      // exception handling, which knows nothing about this route -- so the 500 went out without `Connection: close`
      // and left an unread body behind. And `onRejected` is wired through `tapError`, which does not fire for a
      // `Cause.Die`, so the call produced *no* entry at all: a 500 on the one surface whose purpose is attributing
      // every call, invisible to an operator querying the documented prefix.
      run(
        postDeclaring(aboveAggregationThreshold)(selectQuery, "application/sparql-query", Some(token)),
      ).map((response, entries) =>
        assertTrue(
          response.status == Status.InternalServerError,
          headerValue(response, "Connection").contains("close"),
          entries.size == 1,
          outcomeOf(entries.head) == "defect",
        ),
      ).provide(
        envWith(SparqlPassthroughTestEnv.authenticatorAnswering(_ => ZIO.die(new IllegalStateException("boom")))),
      )
    },
    test("the matched route renames the request's span and is recorded as http.route") {
      // Keeps span names low-cardinality per the OTel HTTP semconv: the middleware opens the span as the bare
      // method, and this interceptor narrows it to the path template once routing has succeeded.
      runInSpan(post(selectQuery, "application/sparql-query", Some(token)))
        .map((name, route) => assertTrue(name == "POST /admin/sparql/query", route.contains("/admin/sparql/query")))
        .provide(env(systemAdmin))
    },
    test("an audit entry carries the trace and span ids, which is the log-to-trace jump on this surface") {
      // Asserted through the server's real tracing middleware rather than a rebuilt one: the annotations are its
      // behaviour, and nothing else pinned that a passthrough entry can be followed into a trace.
      runTraced(post(selectQuery, "application/sparql-query", Some(token)))
        .map(entries =>
          assertTrue(
            entries.size == 1,
            entries.head.annotations.get("trace_id").exists(_.matches("[0-9a-f]{32}")),
            entries.head.annotations.get("span_id").exists(_.matches("[0-9a-f]{16}")),
            !entries.head.annotations.get("trace_id").contains("0" * 32),
          ),
        )
        .provide(env(systemAdmin))
    },
  ) @@ TestAspect.sequential
}
