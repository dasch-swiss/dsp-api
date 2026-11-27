/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiProblem.InternalServerError
import swiss.dasch.api.ApiProblem.UnprocessableContent
import swiss.dasch.domain.IngestRejected
import swiss.dasch.infrastructure.CorrelationId
import zio.*

/** The one mapping of ingest failures to the error body; a new failure type gets a case here. */
object IngestFailure {

  val internalErrorCode: String = "internal"

  private val internalMessage = "An internal error occurred. Report the correlation_id to DaSCH support."

  def errorCode(t: Throwable): String = t match {
    case IngestRejected(code, _) => code
    case _                       => internalErrorCode
  }

  def toApiProblem(t: Throwable): URIO[Any, ApiProblem] =
    t match {
      case IngestRejected(code, message) =>
        CorrelationId.current.map(id => UnprocessableContent(id.value, code, message))
      case _ => internal("Ingest request failed", Cause.fail(t))
    }

  /**
   * Logs `cause` and builds the structured 500 with the same correlation ID.
   *
   * The log is annotated here too, because outside [[CorrelationId.scoped]] `current` gives a fresh ID that is not in
   * the annotations yet.
   */
  private[api] def internal(message: String, cause: Cause[Any]): UIO[InternalServerError] =
    CorrelationId.current.flatMap { id =>
      ZIO
        .logAnnotate(CorrelationId.annotationKey, id.value)(ZIO.logErrorCause(message, cause))
        .as(InternalServerError(id.value, internalErrorCode, internalMessage))
    }
}
