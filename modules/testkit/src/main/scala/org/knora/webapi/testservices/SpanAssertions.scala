/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.SpanData
import zio.test.TestResult
import zio.test.assertTrue

/**
 * Reusable assertions over the spans captured by [[InMemoryTracing]].
 *
 * Each helper takes the finished spans (from `InMemoryTracing.finishedSpans`) plus the name of the
 * span under test and returns a zio-test [[TestResult]], so several can be combined with `&&`.
 * Helpers that target a single span fail if no span with that name was finished.
 */
object SpanAssertions {

  /** Attribute key for the Gravsearch query exit reason, asserted by [[hasGravsearchExitReason]]. */
  val GravsearchExitReasonKey: AttributeKey[String] = AttributeKey.stringKey("gravsearch.exit_reason")

  /** The first finished span with the given name, if any. Escape hatch for ad-hoc assertions. */
  def findSpan(spans: Seq[SpanData], name: String): Option[SpanData] =
    spans.find(_.getName == name)

  /** A span with the given name was finished. */
  def hasSpan(spans: Seq[SpanData], name: String): TestResult =
    assertTrue(spans.exists(_.getName == name))

  /** No span with the given name was finished. */
  def hasNoSpan(spans: Seq[SpanData], name: String): TestResult =
    assertTrue(!spans.exists(_.getName == name))

  /**
   * `parent` is the direct parent of `child`, matched by span-id within the same trace. Fails if
   * either span is missing.
   */
  def isParentChild(spans: Seq[SpanData], parent: String, child: String): TestResult =
    assertTrue(
      (for {
        p <- findSpan(spans, parent)
        c <- findSpan(spans, child)
      } yield c.getParentSpanId == p.getSpanId && c.getTraceId == p.getTraceId).contains(true),
    )

  /** The named span carries `key` with exactly `value`. */
  def hasAttribute[A](spans: Seq[SpanData], name: String, key: AttributeKey[A], value: A): TestResult =
    assertTrue(findSpan(spans, name).flatMap(s => Option(s.getAttributes.get(key))).contains(value))

  /** The named span carries `key` with any value. */
  def hasAttributeKey(spans: Seq[SpanData], name: String, key: AttributeKey[?]): TestResult =
    assertTrue(findSpan(spans, name).exists(s => Option(s.getAttributes.get(key)).isDefined))

  /** The named span finished with status code ERROR. */
  def hasErrorStatus(spans: Seq[SpanData], name: String): TestResult =
    assertTrue(findSpan(spans, name).exists(_.getStatus.getStatusCode == StatusCode.ERROR))

  /** The named span's status description equals `description`. */
  def hasStatusDescription(spans: Seq[SpanData], name: String, description: String): TestResult =
    assertTrue(findSpan(spans, name).exists(_.getStatus.getDescription == description))

  /** The named span carries `gravsearch.exit_reason` equal to `reason`. */
  def hasGravsearchExitReason(spans: Seq[SpanData], name: String, reason: String): TestResult =
    hasAttribute(spans, name, GravsearchExitReasonKey, reason)
}
