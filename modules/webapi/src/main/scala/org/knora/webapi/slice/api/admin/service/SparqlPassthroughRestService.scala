/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

import io.opentelemetry.api.trace.Span
import sttp.model.MediaType
import sttp.model.QueryParams
import sttp.model.StatusCode
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing

import java.nio.charset.StandardCharsets

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.admin.SparqlPassthroughAudit
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
      .map(response =>
        // A store that sent no Content-Type still needs one on the way out, and the relayed body is declared with a
        // wildcard media type, which is not a legal Content-Type to emit. Fuseki always sends one, so this is the
        // defensive branch for a future backend behind the same seam.
        (
          response.contentType.orElse(Some(MediaType.ApplicationOctetStream.toString)),
          response.body,
          response.status,
        ),
      )

  private def runQuery(
    user: User,
    sparql: String,
    accept: Option[String],
    params: QueryParams,
  ): Task[RawSparqlResponse] =
    for {
      // Defence in depth, both of these: the endpoint's security logic already refused a caller who is not a
      // SystemAdmin, and the route is not registered at all when the flag is off. They are kept so that a future
      // wiring mistake -- this service reached through a differently secured endpoint -- fails closed. The
      // authorization check stays ahead of the flag re-check so that even then a caller could not learn from the
      // response whether this deployment has the passthrough enabled.
      _        <- auth.ensureSystemAdmin(user)
      _        <- ensureEnabled
      _        <- rejectSparqlInQueryString(params)
      _        <- ensureWithinRequestCap(sparql)
      request   = RawSparqlRequest(sparql, accept, relayedDatasetParams(params))
      response <- withBackstop(triplestoreService.rawQuery(request))
    } yield response

  private val ensureEnabled: IO[ForbiddenException, Unit] =
    ZIO
      .fail(ForbiddenException("The SPARQL passthrough is not enabled on this deployment."))
      .unless(appConfig.allowSparqlPassthrough)
      .unit

  /**
   * A query sent as `?query=...` is refused rather than ignored.
   *
   * Ignoring it would be worse than it looks: by the time the request reaches this code its SPARQL text has already
   * been written to the edge proxy's access log, which is outside this surface's logging controls -- and the caller
   * would see only an opaque body-decode failure, so the leak would go unnoticed. An explicit error makes the mistake
   * visible and correctable.
   */
  private def rejectSparqlInQueryString(params: QueryParams): IO[BadRequestException, Unit] =
    params.toSeq.map(_._1).find(SparqlPassthroughRestService.bodyOnlyParams.contains) match {
      case Some(name) =>
        ZIO.fail(
          BadRequestException(
            s"The SPARQL statement must be sent in the request body, not as the '$name' query-string parameter, " +
              "because a query string is recorded in access logs.",
          ),
        )
      case None => ZIO.unit
    }

  /**
   * Rejects an over-cap request body with `413`.
   *
   * The memory bound is not here. The endpoint carries the same cap as a framework request-body limit, which bounds
   * the body *stream* and so refuses an over-cap request after reading at most that many bytes; by the time this
   * runs the body has already been materialised, within that bound. This check is the fallback that keeps the
   * documented `413` -- with this surface's typed body and its audit entry -- if the framework attribute is ever
   * dropped, and it is the one place the limit is stated in terms the caller sees.
   *
   * Both live on this one endpoint and must stay that way: the same limit applied server-wide would reject the
   * larger project-import upload.
   */
  private def ensureWithinRequestCap(sparql: String): IO[SparqlRequestTooLargeException, Unit] =
    ZIO
      .fail(SparqlRequestTooLargeException.make(config.maxRequestBodyBytes))
      .when(utf8Bytes(sparql) > config.maxRequestBodyBytes)
      .unit

  /** Only the two SPARQL-protocol dataset parameters are relayed; anything else the caller sent is dropped. */
  private def relayedDatasetParams(params: QueryParams): Map[String, Seq[String]] =
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
   * The entry is emitted from `onExit`, and that is load-bearing three times over. Half the outcomes are not
   * failures -- a store error arrives as a *successful* response carrying the store's status, while a cap breach, a
   * rejection or an unavailable store arrive as failures -- so a trailing `logInfo` would record only some of the
   * calls this surface must attribute. An abandoned call is neither: an interruption produces no value and no typed
   * failure, and `tapBoth` would let it pass unrecorded, which is exactly the call an audit trail most needs. And
   * `onExit` is uninterruptible, so the entry is still written while the fiber is being torn down.
   *
   * The `403` from the service's own authorization backstop is inside this effect on purpose, so a forbidden attempt
   * is attributed by the same emitter. The `401`, and the `403` from the endpoint's security logic, are not
   * observable here -- neither request reaches this service -- and are attributed by the server's hooks and the
   * security logic itself.
   */
  private def observed(user: User, sparql: String)(effect: Task[RawSparqlResponse]): Task[RawSparqlResponse] =
    SanitizedSpan.withSpan(tracing, SparqlPassthroughRestService.spanName, SparqlPassthroughRestService.exitReasonKey) {
      span =>
        Clock.nanoTime.flatMap { started =>
          effect.onExit {
            case Exit.Success(response) => report(span, user, sparql, started, outcomeOf(response), Some(response))
            case Exit.Failure(cause)    => report(span, user, sparql, started, outcomeOf(cause), None)
          }
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
      val requestBytes  = utf8Bytes(sparql)
      val responseBytes = response.map(_.body.length)
      val attributes    = SparqlPassthroughRestService.spanAttributes(outcome, durationMs, requestBytes, response)
      ZIO.succeed {
        attributes.strings.foreach { case (key, value) => val _ = span.setAttribute(key, value) }
        attributes.longs.foreach { case (key, value) => val _ = span.setAttribute(key, value) }
      } *> SparqlPassthroughAudit(
        outcome = outcome,
        user = Some(user),
        durationMs = Some(durationMs),
        storeStatus = response.map(_.status.code),
        responseBytes = responseBytes,
        requestBytes = Some(requestBytes),
        // The statement is the audit trail of what was run, so it is recorded -- but only for a call that actually
        // reached the store. For one refused before then it is unbounded caller-supplied text with nothing to
        // attribute, so only its size is kept.
        statement = Option.when(!SparqlPassthroughRestService.outcomesBeforeForwarding.contains(outcome))(sparql),
      ).log
    }

  private def utf8Bytes(s: String): Int = s.getBytes(StandardCharsets.UTF_8).length

  private def outcomeOf(response: RawSparqlResponse): String =
    if (response.status.isSuccess) "ok" else "store-error"

  private def outcomeOf(cause: Cause[Throwable]): String =
    cause.failureOption
      .map(outcomeOf)
      .getOrElse(if (cause.isInterrupted) "interrupted" else "defect")

  private def outcomeOf(error: Throwable): String = error match {
    case e: SparqlPassthroughException => e.outcome
    case _: ForbiddenException         => "forbidden"
    case _: BadRequestException        => "bad-request"
    case _                             => "error"
  }
}

object SparqlPassthroughRestService {

  private val spanName = "admin.sparql.query"

  /** The attribute an interrupted passthrough span carries; the counterpart of Gravsearch's own exit reason. */
  private[service] val exitReasonKey = "sparql_passthrough.exit_reason"

  /** Query-string parameters that carry a SPARQL statement, and are therefore refused on this surface. */
  private val bodyOnlyParams: Set[String] = Set("query", "update")

  /** The SPARQL-protocol query dataset parameters, the only query-string parameters relayed to the store. */
  private val datasetParams: Set[String] = Set("default-graph-uri", "named-graph-uri")

  /** Outcomes decided before anything was forwarded to the store, for which the statement text is not logged. */
  private[service] val outcomesBeforeForwarding: Set[String] =
    Set("forbidden", "bad-request", "request-cap-exceeded", "overloaded")

  /** The span's attributes, split by type because OpenTelemetry attributes are typed. */
  private[service] final case class SpanAttributes(strings: Seq[(String, String)], longs: Seq[(String, Long)])

  /**
   * The span's attribute set, derived as data so its shape is pinned by test rather than by reading the writer.
   *
   * Everything here is bounded by construction: two fixed-vocabulary strings and four counters. Neither the
   * statement text nor any part of the result payload appears -- a span attribute must stay bounded, and the
   * statement belongs in the log entry, which is size-capped separately.
   */
  private[service] def spanAttributes(
    outcome: String,
    durationMs: Long,
    requestBytes: Int,
    response: Option[RawSparqlResponse],
  ): SpanAttributes =
    SpanAttributes(
      strings = Seq(
        "sparql_passthrough.operation" -> "query",
        "sparql_passthrough.outcome"   -> outcome,
      ),
      longs = Seq(
        "sparql_passthrough.duration_ms"   -> durationMs,
        "sparql_passthrough.request_bytes" -> requestBytes.toLong,
      ) ++ response.toSeq.flatMap(r =>
        Seq(
          "sparql_passthrough.response_bytes" -> r.body.length.toLong,
          "sparql_passthrough.store_status"   -> r.status.code.toLong,
        ),
      ),
    )

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
