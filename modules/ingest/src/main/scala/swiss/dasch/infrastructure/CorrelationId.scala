/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import zio.*

import java.util.UUID

/**
 * The ID that links an ingest error body to its log lines, its trace and sipi's log lines.
 *
 * It lives in the `correlation_id` log annotation. Inside a dsp-api request it is the request's `trace_id`, so the
 * ID a client reports also finds the trace.
 */
final case class CorrelationId(value: String) extends AnyVal

object CorrelationId {

  val annotationKey: String = "correlation_id"

  private val traceIdKey = "trace_id"

  def makeNew: UIO[CorrelationId] = ZIO.succeed(CorrelationId(UUID.randomUUID().toString.replace("-", "")))

  /** The ID of the current scope; a fresh one when the effect runs outside [[scoped]] or [[withNew]]. */
  val current: UIO[CorrelationId] =
    ZIO.logAnnotations.flatMap(_.get(annotationKey).fold(makeNew)(id => ZIO.succeed(CorrelationId(id))))

  /** Runs `effect` under the existing correlation ID, else the request's `trace_id`, else a fresh ID. */
  def scoped[R, E, A](effect: ZIO[R, E, A]): ZIO[R, E, A] =
    ZIO.logAnnotations.flatMap { annotations =>
      annotations
        .get(annotationKey)
        .orElse(annotations.get(traceIdKey))
        .fold(makeNew)(id => ZIO.succeed(CorrelationId(id)))
        .flatMap(id => ZIO.logAnnotate(annotationKey, id.value)(effect))
    }

  /** Runs `effect` under a fresh correlation ID, e.g. one per file of a bulk ingest. */
  def withNew[R, E, A](effect: ZIO[R, E, A]): ZIO[R, E, A] =
    makeNew.flatMap(id => ZIO.logAnnotate(annotationKey, id.value)(effect))
}
