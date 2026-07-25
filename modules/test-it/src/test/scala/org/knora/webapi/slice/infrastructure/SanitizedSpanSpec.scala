/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import org.junit.runner.RunWith
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.testservices.InMemoryTracing
import org.knora.webapi.testservices.SpanAssertions

/**
 * Guards the one property [[SanitizedSpan]] exists for: a failure inside the span must never put the failure's own
 * message -- which on the paths that use this helper echoes user-supplied text such as a SPARQL query -- into the span
 * status description.
 *
 * This spec lives in the integration-test module only because the OpenTelemetry in-memory span exporter is on this
 * module's test classpath. It needs no container.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class SanitizedSpanSpec extends ZIOSpecDefault {

  private val spanName = "admin.sparql.query"

  /** The caller's own exit-reason key; the helper takes it rather than owning one, so each vertical keeps its own. */
  private val exitReasonKey = "sparql_passthrough.exit_reason"

  /** Stands in for user-supplied text carried by a failure message, e.g. a SPARQL literal. */
  private val sentinel = "SENSITIVE-QUERY-LITERAL-4d21"

  private val errorTypeKey = AttributeKey.stringKey("error.type")

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("SanitizedSpan.withSpan")(
      test("a typed failure yields an ERROR span whose description is the class name only") {
        (for {
          tracing <- ZIO.service[Tracing]
          _       <- SanitizedSpan
                 .withSpan(tracing, spanName, exitReasonKey)(_ => ZIO.fail(new IllegalStateException(sentinel)))
                 .either
          spans <- InMemoryTracing.finishedSpans
        } yield {
          val description = SpanAssertions.findSpan(spans, spanName).flatMap(s => Option(s.getStatus.getDescription))
          SpanAssertions.hasErrorStatus(spans, spanName) &&
          // Equality, not containment. If the status mapper were relaxed from UNSET to ERROR, the tracing library's
          // own setter would run after ours and replace this description with `cause.prettyPrint` -- the message and
          // stacktrace. This equality is what breaks in that case.
          SpanAssertions.hasStatusDescription(spans, spanName, s"$spanName: IllegalStateException") &&
          SpanAssertions.hasAttribute(spans, spanName, errorTypeKey, "IllegalStateException") &&
          assertTrue(description.exists(!_.contains(sentinel)))
        }).provide(InMemoryTracing.layer)
      },
      test("no exception event is attached, so the message is not leaked as a span event either") {
        (for {
          tracing <- ZIO.service[Tracing]
          _       <- SanitizedSpan
                 .withSpan(tracing, spanName, exitReasonKey)(_ => ZIO.fail(new IllegalStateException(sentinel)))
                 .either
          spans <- InMemoryTracing.finishedSpans
        } yield assertTrue(
          SpanAssertions.findSpan(spans, spanName).exists(_.getEvents.isEmpty),
          SpanAssertions.findSpan(spans, spanName).exists(span => !span.toString.contains(sentinel)),
        )).provide(InMemoryTracing.layer)
      },
      test("the span handle is passed to the body, so a caller can attach its own bounded attributes") {
        (for {
          tracing <- ZIO.service[Tracing]
          _       <- SanitizedSpan.withSpan(tracing, spanName, exitReasonKey)(span =>
                 ZIO.succeed { val _ = span.setAttribute("sparql_passthrough.outcome", "ok") },
               )
          spans <- InMemoryTracing.finishedSpans
        } yield SpanAssertions
          .hasAttribute(spans, spanName, AttributeKey.stringKey("sparql_passthrough.outcome"), "ok"))
          .provide(InMemoryTracing.layer)
      },
      test("a successful call leaves the span status unset rather than ERROR") {
        (for {
          tracing <- ZIO.service[Tracing]
          _       <- SanitizedSpan.withSpan(tracing, spanName, exitReasonKey)(_ => ZIO.succeed(1))
          spans   <- InMemoryTracing.finishedSpans
        } yield assertTrue(
          SpanAssertions
            .findSpan(spans, spanName)
            .exists(_.getStatus.getStatusCode != StatusCode.ERROR),
        )).provide(InMemoryTracing.layer)
      },
      test("an interrupt marks the open span with the caller's exit reason and ERROR") {
        // Without this branch an abandoned call is indistinguishable from one that simply finished: interruption
        // produces no typed failure, so the sanitized-error path above never runs, and OpenTelemetry has no
        // `cancelled` status to express the difference. The latch guarantees the span is open when the interrupt
        // lands, which is the case that matters -- a client disconnecting mid-query.
        (for {
          tracing <- ZIO.service[Tracing]
          started <- Promise.make[Nothing, Unit]
          fiber   <- SanitizedSpan.withSpan(tracing, spanName, exitReasonKey)(_ => started.succeed(()) *> ZIO.never).fork
          _       <- started.await
          _       <- fiber.interrupt
          spans   <- InMemoryTracing.finishedSpans
        } yield SpanAssertions.hasAttribute(spans, spanName, AttributeKey.stringKey(exitReasonKey), "interrupted") &&
          SpanAssertions.hasErrorStatus(spans, spanName)).provide(InMemoryTracing.layer)
      },
    )
}
