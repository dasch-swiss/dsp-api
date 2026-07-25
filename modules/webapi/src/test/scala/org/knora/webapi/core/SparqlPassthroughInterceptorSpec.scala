/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.junit.runner.RunWith
import sttp.model.StatusCode as SttpStatusCode
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.*
import zio.http.*
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.context.ContextStorage
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

  private def envWith(authenticator: Authenticator) =
    SparqlPassthroughTestEnv.layerWithAuthenticator(storeAnswersOk, authenticator) ++ OpenTelemetry.contextZIO

  private val routes =
    for {
      appConfig   <- ZIO.service[AppConfig]
      endpoints   <- ZIO.service[SparqlPassthroughEndpoints]
      restService <- ZIO.service[SparqlPassthroughRestService]
      ctxStore    <- ZIO.service[ContextStorage]
    } yield ZioHttpInterpreter(DspApiServer.serverOptions(ctxStore))
      .toHttp(new SparqlPassthroughServerEndpoints(appConfig, endpoints, restService).serverEndpoints)

  /** Runs one request through the real interceptor chain, returning the response and the entries it produced. */
  private def run(request: Request) =
    ZIO.scoped {
      for {
        app      <- routes
        response <- app.runZIO(request)
        entries  <- ZTestLogger.logOutput.map(_.map(_.message()).filter(_.startsWith("SPARQL passthrough:")))
      } yield (response, entries)
    }

  private def outcomeOf(entry: String): String =
    entry.split(' ').collectFirst { case kv if kv.startsWith("outcome=") => kv.drop("outcome=".length) }.getOrElse("")

  private def url(p: String): URL = URL.decode(p).getOrElse(throw new IllegalArgumentException(p))

  /**
   * `declareLength` is load-bearing rather than incidental: whether a rejection closes the connection is decided
   * from the request's `Content-Length`, since that is what says whether the server had already aggregated the body.
   * A real client sends one; an in-memory `Request` does not get one for free, so it is set explicitly. Omitting it
   * is how this spec models the chunked case, where nothing guarantees the body was read.
   */
  private def post(
    body: String,
    contentType: String,
    bearer: Option[String],
    declareLength: Boolean = true,
  ): Request = {
    val base = Request
      .post(url(path), Body.fromString(body))
      .addHeader(Header.Custom("Content-Type", contentType))
    val sized = if (declareLength) base.addHeader(Header.ContentLength(body.length.toLong)) else base
    bearer.fold(sized)(t => sized.addHeader(Header.Custom("Authorization", s"Bearer $t")))
  }

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
    test("a rejection on a body of unknown length still closes it") {
      // The other half of the gate: without a Content-Length the body may be streamed, so it may be sitting unread.
      run(post(selectQuery, "text/plain", Some(token), declareLength = false))
        .map((response, _) => assertTrue(headerValue(response, "Connection").contains("close")))
        .provide(env(systemAdmin))
    },
    test("a defect in the security logic is answered through the same hook, and closes the connection too") {
      // A defect used to escape the security logic entirely and be answered by tapir's outer exception handling,
      // which knows nothing about this route -- so the 500 went out without `Connection: close` and left the same
      // unread body behind. Converting it to a typed failure puts it back on the hook that does know.
      run(
        post(selectQuery, "application/sparql-query", Some(token), declareLength = false),
      ).map((response, _) =>
        assertTrue(
          response.status == Status.InternalServerError,
          headerValue(response, "Connection").contains("close"),
        ),
      ).provide(
        envWith(SparqlPassthroughTestEnv.authenticatorAnswering(_ => ZIO.die(new IllegalStateException("boom")))),
      )
    },
  ) @@ TestAspect.sequential
}
