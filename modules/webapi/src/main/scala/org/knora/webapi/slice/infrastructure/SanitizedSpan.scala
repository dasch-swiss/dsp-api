/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import zio.*
import zio.telemetry.opentelemetry.tracing.StatusMapper
import zio.telemetry.opentelemetry.tracing.Tracing

/**
 * Opens a span whose failure status can never carry the message of the failure that produced it.
 *
 * This exists because the default behaviour leaks. zio-telemetry's failure handling does, in effect:
 *
 * {{{
 *   if (statusCode == ERROR) span.setStatus(ERROR, cause.prettyPrint)
 *   else                     span.setStatus(statusCode)
 * }}}
 *
 * `cause.prettyPrint` is the error string plus a stacktrace, and on a path that handles user-supplied text -- a SPARQL
 * query, a search filter -- that string echoes the user's own input straight into the span status description and on
 * to whatever backend the traces are exported to. Mapping the failure status to `UNSET`, which the OpenTelemetry SDK
 * treats as a no-op, is the only thing that prevents it; the sanitized status is then set explicitly.
 *
 * The order matters and is not enforced by the SDK: the library's setter runs after ours, so if the mapper is ever
 * changed to `ERROR` it silently overwrites the sanitized description and the leak returns. That is why the guard is a
 * test asserting the description's exact value, rather than a comment.
 *
 * See `docs/observability/instrumentation-recipe.md`. `SearchResponderV2.stageSpan` delegates here for the Gravsearch
 * stage spans, passing its own `gravsearch.exit_reason` key.
 */
object SanitizedSpan {

  /**
   * LOAD-BEARING: must map to `UNSET`, never `ERROR`. See the object documentation -- mapping to `ERROR` here
   * reinstates the `cause.prettyPrint` leak in one edit.
   */
  private val unsetOnFailure: StatusMapper[Throwable, Any] =
    StatusMapper.failureNoException[Throwable](_ => StatusCode.UNSET)

  /**
   * Runs `f` inside an `INTERNAL` span named `name`, handing it the raw span so it can attach its own bounded
   * attributes. A failure leaves the span with status `ERROR`, a description of exactly `"<name>: <ClassName>"`, and
   * an `error.type` attribute -- no message, no stacktrace, and no exception event.
   *
   * An interruption is marked separately, with `exitReasonKey` set to `interrupted` and status `ERROR "interrupted"`.
   * It needs its own branch because an interrupted effect produces no typed failure, so without this an abandoned
   * call would be indistinguishable from one that simply finished -- and OpenTelemetry has no `cancelled` status to
   * express the difference. The key is the caller's, because it belongs to the caller's attribute namespace and is
   * what its trace queries select on.
   *
   * The span is captured up front rather than resolved in the finalizer, because during interruption teardown the
   * current span no longer points at this one. Both finalizers run before the library's span-end, so their writes
   * land, and the library's own status setter then runs with `unsetOnFailure` (a no-op) and leaves them intact.
   */
  def withSpan[R, A](tracing: Tracing, name: String, exitReasonKey: String)(
    f: Span => ZIO[R, Throwable, A],
  ): ZIO[R, Throwable, A] =
    tracing.span(name, SpanKind.INTERNAL, statusMapper = unsetOnFailure) {
      tracing.getCurrentSpanUnsafe.flatMap { span =>
        f(span)
          .tapErrorCause(cause => ZIO.succeed(markSanitizedError(span, name, cause)))
          .onExit {
            case Exit.Failure(cause) if cause.isInterrupted =>
              ZIO.succeed {
                val _ = span.setAttribute(exitReasonKey, "interrupted")
                val _ = span.setStatus(StatusCode.ERROR, "interrupted")
              }
            case _ => ZIO.unit
          }
      }
    }

  /** Writes the sanitized `ERROR` status (`"<name>: <ClassName>"`, no message) and `error.type` onto the span. */
  private def markSanitizedError(span: Span, name: String, cause: Cause[Throwable]): Unit = {
    val kind = cause.failureOption.map(_.getClass.getSimpleName).getOrElse("defect")
    val _    = span.setStatus(StatusCode.ERROR, s"$name: $kind")
    cause.failureOption.foreach { error =>
      val _ = span.setAttribute("error.type", error.getClass.getSimpleName)
    }
  }
}
