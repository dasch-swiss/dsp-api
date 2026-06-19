/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import io.opentelemetry.api.common.AttributeKey
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test.*

import org.knora.webapi.testservices.InMemoryTracing
import org.knora.webapi.testservices.SpanAssertions

/**
 * Tests the `SearchResponderV2.stageSpan` helper (Decision 3) against the in-memory span harness.
 *
 * These lock the two error-handling guarantees that are one edit away from silently regressing:
 *   - a stage failure produces an ERROR span whose status description is exactly `"<stage>: <Class>"`,
 *     never the raw exception message/stacktrace, and records no exception event (REQ-1.6);
 *   - an interruption marks the open stage span with `gravsearch.exit_reason=interrupted` + ERROR (REQ-1.11).
 *
 * The description-equality assertion is the regression lock against the `unsetOnFailure` brittleness:
 * if the mapper is ever changed from UNSET to ERROR, zio-telemetry would overwrite the sanitized
 * description with `cause.prettyPrint` (which echoes the offending FILTER literal) and this test fails.
 */
object SearchResponderV2StageSpanSpec extends ZIOSpecDefault {

  private val stage = "gravsearch.prequery.execute"

  // A recognizable secret that must never reach the span if sanitization holds.
  private val sentinel = "SENSITIVE-FILTER-LITERAL-7f3a"

  private val errorTypeKey = AttributeKey.stringKey("error.type")

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("SearchResponderV2.stageSpan")(
      test("a stage failure yields an ERROR span with a sanitized description and no leaked message") {
        (for {
          tracing <- ZIO.service[Tracing]
          _       <- SearchResponderV2
                 .stageSpan(tracing, stage)(ZIO.fail(new RuntimeException(sentinel)))
                 .either
          spans <- InMemoryTracing.finishedSpans
        } yield {
          val description = SpanAssertions.findSpan(spans, stage).flatMap(s => Option(s.getStatus.getDescription))
          SpanAssertions.hasErrorStatus(spans, stage) &&
          SpanAssertions.hasStatusDescription(spans, stage, s"$stage: RuntimeException") &&
          SpanAssertions.hasAttribute(spans, stage, errorTypeKey, "RuntimeException") &&
          assertTrue(description.exists(!_.contains(sentinel))) &&
          assertTrue(SpanAssertions.findSpan(spans, stage).exists(_.getEvents.isEmpty))
        }).provide(InMemoryTracing.layer)
      },
      test("an interrupt marks the open stage span with gravsearch.exit_reason=interrupted and ERROR") {
        (for {
          tracing <- ZIO.service[Tracing]
          started <- Promise.make[Nothing, Unit]
          // The latch guarantees the span body is running (span open) before we interrupt, mirroring a real
          // interruption mid-stage (e.g. a triplestore query in flight) rather than interrupting before the
          // span body starts.
          fiber <- SearchResponderV2.stageSpan(tracing, stage)(started.succeed(()) *> ZIO.never).fork
          _     <- started.await
          _     <- fiber.interrupt
          spans <- InMemoryTracing.finishedSpans
        } yield SpanAssertions.hasGravsearchExitReason(spans, stage, "interrupted") &&
          SpanAssertions.hasErrorStatus(spans, stage)).provide(InMemoryTracing.layer)
      },
    )
}
