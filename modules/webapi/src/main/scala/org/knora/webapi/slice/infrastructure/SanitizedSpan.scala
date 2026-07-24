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
 * See `docs/observability/instrumentation-recipe.md`. `SearchResponderV2` implements the same pattern inline for the
 * Gravsearch stage spans, with a vertical-specific interruption attribute.
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
   * The span is captured up front rather than resolved in the finalizer, because during interruption teardown the
   * current span no longer points at this one.
   */
  def withSpan[R, A](tracing: Tracing, name: String)(f: Span => ZIO[R, Throwable, A]): ZIO[R, Throwable, A] =
    tracing.span(name, SpanKind.INTERNAL, statusMapper = unsetOnFailure) {
      tracing.getCurrentSpanUnsafe.flatMap { span =>
        f(span).tapErrorCause(cause => ZIO.succeed(markSanitizedError(span, name, cause)))
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
