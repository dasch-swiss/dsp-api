/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test.*

object InMemoryTracingSpec extends ZIOSpecDefault {

  private val tracing = ZIO.serviceWithZIO[Tracing]

  // A fresh layer (and thus a fresh in-memory exporter) per test, so span counts are isolated.
  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("InMemoryTracing")(
      test("a trivial effect wrapped in tracing.span(\"x\") yields one finished span named x") {
        (for {
          _     <- tracing(_.span("x")(ZIO.unit))
          spans <- InMemoryTracing.finishedSpans
        } yield assertTrue(spans.size == 1) && SpanAssertions.hasSpan(spans, "x"))
          .provide(InMemoryTracing.layer)
      },
      test("nested spans are captured as a parent-child pair") {
        (for {
          _     <- tracing(t => t.span("parent")(t.span("child")(ZIO.unit)))
          spans <- InMemoryTracing.finishedSpans
        } yield SpanAssertions.hasSpan(spans, "parent") &&
          SpanAssertions.hasSpan(spans, "child") &&
          SpanAssertions.isParentChild(spans, "parent", "child"))
          .provide(InMemoryTracing.layer)
      },
      test("assertion helpers cover attribute, status and gravsearch.exit_reason") {
        (for {
          _     <- tracing(t => t.span("attributed")(t.setAttribute(SpanAssertions.GravsearchExitReasonKey, "limit")))
          _     <- tracing(_.span("failing")(ZIO.fail(new RuntimeException("boom")))).either
          spans <- InMemoryTracing.finishedSpans
        } yield SpanAssertions.hasGravsearchExitReason(spans, "attributed", "limit") &&
          SpanAssertions.hasAttributeKey(spans, "attributed", SpanAssertions.GravsearchExitReasonKey) &&
          SpanAssertions.hasNoSpan(spans, "missing") &&
          SpanAssertions.hasErrorStatus(spans, "failing"))
          .provide(InMemoryTracing.layer)
      },
    )
}
