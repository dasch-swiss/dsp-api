/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

import io.opentelemetry.api.trace.Span
import sttp.model.QueryParams
import sttp.model.StatusCode
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing

import java.nio.charset.StandardCharsets

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.infrastructure.SanitizedSpan
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.RawSparqlRequest
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.RawSparqlResponse
import org.knora.webapi.store.triplestore.errors.SparqlPassthroughException
import org.knora.webapi.store.triplestore.errors.SparqlPassthroughOverloadedException
import org.knora.webapi.store.triplestore.errors.SparqlRequestTooLargeException

/**
 * Authorization, guardrails and attribution for the admin SPARQL passthrough. The query text itself is never
 * inspected: this service decides *whether* a call may run and *how much* it may consume, and hands the text on
 * unchanged.
 *
 * `inFlight` is the surface-wide backstop counter. It is a `Ref` rather than a `zio.Semaphore` because a semaphore
 * offers no non-blocking acquire, and `withPermit` would make an over-limit call queue instead of being rejected --
 * heap would stay bounded, but fibers and connections would accumulate, which is the failure mode the backstop
 * exists to prevent.
 */
final class SparqlPassthroughRestService(
  private val appConfig: AppConfig,
  private val auth: AuthorizationRestService,
  private val triplestoreService: TriplestoreService,
  private val tracing: Tracing,
  private val inFlight: Ref[Int],
) {

  private def config = appConfig.triplestore.sparqlPassthrough

  def query(user: User)(
    sparql: String,
    accept: Option[String],
    params: QueryParams,
  ): Task[(Option[String], Array[Byte], StatusCode)] =
    observed(user, sparql)(runQuery(user, sparql, accept, params))
      .map(response => (response.contentType, response.body.toArray, response.status))

  private def runQuery(
    user: User,
    sparql: String,
    accept: Option[String],
    params: QueryParams,
  ): Task[RawSparqlResponse] =
    for {
      // The authorization check comes first, and before the flag re-check, so a caller who is not a SystemAdmin
      // cannot learn from the response whether this deployment has the passthrough enabled.
      _        <- auth.ensureSystemAdmin(user)
      _        <- ensureEnabled
      _        <- rejectSparqlInQueryString(params)
      _        <- ensureWithinRequestCap(sparql)
      request   = RawSparqlRequest(sparql, accept, datasetParams(params))
      response <- withBackstop(triplestoreService.rawQuery(request))
    } yield response

  /**
   * Defence in depth. When the flag is off no server endpoint is registered, so the route answers `404` and this is
   * unreachable; it exists so that a future wiring mistake fails closed rather than opening the surface.
   */
  private val ensureEnabled: IO[ForbiddenException, Unit] =
    ZIO
      .fail(ForbiddenException("The SPARQL passthrough is not enabled on this deployment."))
      .unless(appConfig.allowSparqlPassthrough)
      .unit

  /**
   * A query sent as `?query=...` is refused rather than ignored.
   *
   * Ignoring it would be worse than it looks: by the time the request reaches this code its SPARQL text has already
   * been written to the edge proxy's access log and recorded as the `url.query` span attribute, both outside this
   * surface's logging controls -- and the caller would see only an opaque body-decode failure, so the leak would go
   * unnoticed. An explicit error makes the mistake visible and correctable.
   */
  private def rejectSparqlInQueryString(params: QueryParams): IO[BadRequestException, Unit] =
    params.toSeq.map(_._1).find(SparqlPassthroughRestService.bodyOnlyParams.contains) match {
      case Some(name) =>
        ZIO.fail(
          BadRequestException(
            s"The SPARQL statement must be sent in the request body, not as the '$name' query-string parameter, " +
              "because a query string is recorded in access logs and traces.",
          ),
        )
      case None => ZIO.unit
    }

  /**
   * Rejects an over-cap request body with `413`.
   *
   * Be clear about what this is: a **policy check, not a memory bound**. The framework has already materialised the
   * body into a string by the time this runs, so the buffering has happened. Two things keep that acceptable -- the
   * framework evaluates the endpoint's security logic before decoding the body, so only an authenticated SystemAdmin
   * can cause the buffering at all, and the body is always fully consumed, which is what keeps the connection usable
   * for the next request on it. The alternative -- failing the request stream at the cap -- would abort it without
   * draining, leaving the connection unable to serve its next keep-alive request.
   *
   * The check lives here, on this one endpoint, and must stay that way: the same limit applied server-wide would
   * reject the larger project-import upload.
   */
  private def ensureWithinRequestCap(sparql: String): IO[SparqlRequestTooLargeException, Unit] = {
    val submittedBytes = sparql.getBytes(StandardCharsets.UTF_8).length
    ZIO
      .fail(SparqlRequestTooLargeException.make(config.maxRequestBodyBytes))
      .when(submittedBytes > config.maxRequestBodyBytes)
      .unit
  }

  /** Only the two SPARQL-protocol dataset parameters are relayed; anything else the caller sent is dropped. */
  private def datasetParams(params: QueryParams): Map[String, Seq[String]] =
    params.toMultiSeq.filter { case (name, _) => SparqlPassthroughRestService.datasetParams.contains(name) }.toMap

  /**
   * Runs `effect` while holding one of the `maxConcurrentCalls` slots, rejecting rather than waiting when all are
   * taken. `Ref#modify` makes the check-and-increment a single atomic step, so the limit cannot be exceeded by two
   * callers observing the same count.
   *
   * Visible to its own package so the reject-rather-than-queue property can be tested deterministically, by holding
   * a slot open with a latch. Driving the same property through the HTTP path would be a race, since whether two
   * requests genuinely overlap depends on how fast the store answers.
   */
  private[service] def withBackstop[A](effect: IO[SparqlPassthroughException, A]): IO[SparqlPassthroughException, A] =
    ZIO.scoped {
      ZIO
        .acquireRelease(tryEnter)(entered => inFlight.update(_ - 1).when(entered).unit)
        .flatMap {
          case true  => effect
          case false => ZIO.fail(SparqlPassthroughOverloadedException.make(config.maxConcurrentCalls))
        }
    }

  private val tryEnter: UIO[Boolean] =
    inFlight.modify(current => if (current < config.maxConcurrentCalls) (true, current + 1) else (false, current))

  /**
   * Wraps a call in its span and its single log entry.
   *
   * The entry is emitted on exit rather than on the happy path, because half the outcomes are not failures: a store
   * error arrives as a *successful* response carrying the store's status, while a cap breach, a rejection or an
   * unavailable store arrive as failures. A trailing `logInfo` would therefore record only some of the calls this
   * surface is supposed to attribute.
   *
   * The `403` from the authorization check is inside this effect on purpose, so a forbidden attempt is attributed by
   * the same emitter. The `401` is not observable here -- an unauthenticated request never reaches this service --
   * and is logged from the server's security-failure hook instead.
   */
  private def observed(user: User, sparql: String)(effect: Task[RawSparqlResponse]): Task[RawSparqlResponse] =
    SanitizedSpan.withSpan(tracing, SparqlPassthroughRestService.spanName) { span =>
      Clock.nanoTime.flatMap { started =>
        effect.tapBoth(
          error => report(span, user, sparql, started, outcomeOf(error), None),
          response => report(span, user, sparql, started, outcomeOf(response), Some(response)),
        )
      }
    }

  private def report(
    span: Span,
    user: User,
    sparql: String,
    startedNanos: Long,
    outcome: String,
    response: Option[RawSparqlResponse],
  ): UIO[Unit] =
    Clock.nanoTime.flatMap { finishedNanos =>
      val durationMs    = (finishedNanos - startedNanos) / 1000000
      val responseBytes = response.map(_.body.length)
      // No result payload is recorded, here or on the span -- only its size. The statement text is recorded because
      // it is the audit trail of what was run; it is not a span attribute, which must stay bounded.
      ZIO.succeed {
        val _ = span.setAttribute("sparql_passthrough.operation", "query")
        val _ = span.setAttribute("sparql_passthrough.outcome", outcome)
        val _ = span.setAttribute("sparql_passthrough.duration_ms", durationMs)
        val _ = span.setAttribute("sparql_passthrough.request_bytes", sparql.length.toLong)
        responseBytes.foreach(bytes => span.setAttribute("sparql_passthrough.response_bytes", bytes.toLong))
        response.foreach(r => span.setAttribute("sparql_passthrough.store_status", r.status.code.toLong))
      } *> ZIO.logInfo(
        // `user.id` rather than `user.userIri.value`: the latter re-validates the IRI and throws if it does not
        // parse, which would let the log line fail the request it is only supposed to describe.
        s"SPARQL passthrough: operation=query outcome=$outcome " +
          s"user_iri=${user.id} username=${user.username} " +
          s"duration_ms=$durationMs " +
          s"store_status=${response.fold("-")(_.status.code.toString)} " +
          s"response_bytes=${responseBytes.fold("-")(_.toString)} " +
          s"sparql=$sparql",
      )
    }

  private def outcomeOf(response: RawSparqlResponse): String =
    if (response.status.isSuccess) "ok" else "store-error"

  private def outcomeOf(error: Throwable): String = error match {
    case e: SparqlPassthroughException => e.outcome
    case _: ForbiddenException         => "forbidden"
    case _: BadRequestException        => "bad-request"
    case _                             => "error"
  }
}

object SparqlPassthroughRestService {

  private val spanName = "admin.sparql.query"

  /** Query-string parameters that carry a SPARQL statement, and are therefore refused on this surface. */
  private val bodyOnlyParams: Set[String] = Set("query", "update")

  /** The SPARQL-protocol query dataset parameters, the only query-string parameters relayed to the store. */
  private val datasetParams: Set[String] = Set("default-graph-uri", "named-graph-uri")

  /**
   * Hand-written rather than `ZLayer.derive`, because creating the backstop counter is effectful and the counter must
   * be a single instance shared by every call to the surface.
   */
  val layer
    : URLayer[AppConfig & AuthorizationRestService & TriplestoreService & Tracing, SparqlPassthroughRestService] =
    ZLayer {
      for {
        appConfig          <- ZIO.service[AppConfig]
        auth               <- ZIO.service[AuthorizationRestService]
        triplestoreService <- ZIO.service[TriplestoreService]
        tracing            <- ZIO.service[Tracing]
        inFlight           <- Ref.make(0)
      } yield new SparqlPassthroughRestService(appConfig, auth, triplestoreService, tracing, inFlight)
    }
}
