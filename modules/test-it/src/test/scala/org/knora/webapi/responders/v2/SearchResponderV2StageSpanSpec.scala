/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import org.junit.runner.RunWith
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test.*

import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.testservices.InMemoryTracing
import org.knora.webapi.testservices.SpanAssertions

@RunWith(classOf[DspZTestJUnitRunner])
class SearchResponderV2StageSpanSpec extends ZIOSpecDefault {

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
      test("layerFor wires spans into the externally held exporter") {
        // Isolates the E2E seam: a span created through a Tracing built by `layerFor(exporter)` must land in
        // that same externally held `exporter` (which is how the E2E topology spec reads spans).
        val exporter = InMemorySpanExporter.create()
        (for {
          _    <- ZIO.serviceWithZIO[Tracing](_.span("x")(ZIO.unit))
          names = exporter.getFinishedSpanItems.asScala.map(_.getName).toList
        } yield assertTrue(names.contains("x")))
          .provide(InMemoryTracing.layerFor(exporter))
      },
      test("a defect (die) yields an ERROR span and records no exception event") {
        // User-supplied data (e.g. a FILTER literal) reaches the responder through the typed error channel
        // (parse/triplestore failures), which the test above proves is sanitized. A defect is an unexpected
        // code-level throw; the guaranteed invariant here is that no `recordException` event is attached
        // (so the message is not leaked as a span event). The status description for a defect carries the
        // library's `cause.prettyPrint` (a code stacktrace, not user query text) — an accepted residual.
        (for {
          tracing <- ZIO.service[Tracing]
          _       <- SearchResponderV2.stageSpan(tracing, stage)(ZIO.die(new RuntimeException(sentinel))).exit
          spans   <- InMemoryTracing.finishedSpans
        } yield SpanAssertions.hasErrorStatus(spans, stage) &&
          assertTrue(SpanAssertions.findSpan(spans, stage).exists(_.getEvents.isEmpty))).provide(InMemoryTracing.layer)
      },
    )
}
