/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode as OtelStatusCode
import io.opentelemetry.sdk.trace.data.SpanData
import org.junit.runner.RunWith
import sttp.model.StatusCode
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test.*

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.RawSparqlResponse
import org.knora.webapi.testservices.InMemoryTracing
import org.knora.webapi.testservices.SpanAssertions

/**
 * Drives the passthrough's own observation path -- span, attributes and audit entry -- against a real OpenTelemetry
 * exporter, so the attributes are asserted where they land rather than where they are computed.
 *
 * `SparqlPassthroughAuditSpec` already pins the attribute *set* by calling `spanAttributes` directly, but nothing
 * pinned the write: deleting the loop in `report` that copies them onto the span broke no test. This is the missing
 * half, and it needs the in-memory span exporter, which is on this module's test classpath and not on webapi's --
 * the same reason `SanitizedSpanSpec` lives here. It needs no container.
 *
 * `observed` takes its `Tracing` as an argument rather than reading it off the service precisely so a spec can reach
 * it with nothing else wired; the alternative was standing up the whole authorization and repository stack to assert
 * on two attribute writes.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class SparqlPassthroughSpanSpec extends ZIOSpecDefault {

  private val spanName = "admin.sparql.query"

  private val user =
    User(
      "http://rdfh.ch/users/sparql-passthrough-span",
      "username",
      "email@example.com",
      "given name",
      "family name",
      status = true,
      "lang",
      permissions = PermissionsDataADM(),
    )

  /** Stands in for the caller's own text: it belongs in the audit entry and must never reach the span. */
  private val sentinel = "SENSITIVE-QUERY-LITERAL-9c07"

  private val sparql      = s"""SELECT ?s WHERE { ?s ?p "$sentinel" }"""
  private val sparqlBytes = sparql.getBytes(StandardCharsets.UTF_8).length.toLong
  private val stringKey   = (name: String) => AttributeKey.stringKey(name)
  private val longKey     = (name: String) => AttributeKey.longKey(name)

  private def response(status: StatusCode, body: String) =
    RawSparqlResponse(status, Some("application/sparql-results+json"), body.getBytes(StandardCharsets.UTF_8))

  private def attributeNames(spans: Seq[SpanData]): Set[String] =
    SpanAssertions.findSpan(spans, spanName).toSet.flatMap(_.getAttributes.asMap.asScala.keySet.map(_.getKey))

  private def observe(effect: Task[RawSparqlResponse]) =
    for {
      tracing <- ZIO.service[Tracing]
      _       <- SparqlPassthroughRestService.observed(tracing, user, sparql)(effect).exit
      spans   <- InMemoryTracing.finishedSpans
    } yield spans

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("the SPARQL passthrough span")(
      test("a completed call writes every attribute of the derived set onto the span") {
        // Deleting either write loop in `report` fails here. The values are the ones `spanAttributes` derives, so
        // this covers the write; `SparqlPassthroughAuditSpec` covers what the set contains.
        observe(ZIO.succeed(response(StatusCode.Ok, "12345678"))).map { spans =>
          SpanAssertions.hasAttribute(spans, spanName, stringKey("sparql_passthrough.operation"), "query") &&
          SpanAssertions.hasAttribute(spans, spanName, stringKey("sparql_passthrough.outcome"), "ok") &&
          SpanAssertions.hasAttribute(spans, spanName, longKey("sparql_passthrough.request_bytes"), sparqlBytes) &&
          SpanAssertions.hasAttribute(spans, spanName, longKey("sparql_passthrough.response_bytes"), 8L) &&
          SpanAssertions.hasAttribute(spans, spanName, longKey("sparql_passthrough.store_status"), 200L) &&
          SpanAssertions.hasAttributeKey(spans, spanName, longKey("sparql_passthrough.duration_ms"))
        }.provide(InMemoryTracing.layer)
      },
      test("a store error reaches the span's outcome, even though it arrives as a successful value") {
        observe(ZIO.succeed(response(StatusCode.BadRequest, "nope"))).map { spans =>
          SpanAssertions.hasAttribute(spans, spanName, stringKey("sparql_passthrough.outcome"), "store-error") &&
          SpanAssertions.hasAttribute(spans, spanName, longKey("sparql_passthrough.store_status"), 400L)
        }.provide(InMemoryTracing.layer)
      },
      test("an unclassified failure is attributed as `error`, and leaks neither its message nor the statement") {
        // `error` is the catch-all outcome, and the one nothing else exercises. It is also the case where the
        // failure carries user-supplied text, so it doubles as the leak check on this path.
        observe(ZIO.fail(new IllegalStateException(sentinel))).map { spans =>
          val span = SpanAssertions.findSpan(spans, spanName)
          SpanAssertions.hasAttribute(spans, spanName, stringKey("sparql_passthrough.outcome"), "error") &&
          assertTrue(
            // No response, so neither response attribute is written.
            !attributeNames(spans).contains("sparql_passthrough.response_bytes"),
            !attributeNames(spans).contains("sparql_passthrough.store_status"),
            // And nothing anywhere on the span -- attribute, status description or event -- echoes the text.
            span.exists(s => !s.toString.contains(sentinel)),
            span.exists(_.getStatus.getStatusCode == OtelStatusCode.ERROR),
            span.exists(_.getEvents.isEmpty),
          )
        }.provide(InMemoryTracing.layer)
      },
    )
}
