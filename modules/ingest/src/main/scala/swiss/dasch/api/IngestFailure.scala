/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiProblem.InternalServerError
import swiss.dasch.api.ApiProblem.UnprocessableContent
import swiss.dasch.domain.IngestRejected
import swiss.dasch.domain.SipiCliError
import swiss.dasch.domain.SipiReport.Phase
import swiss.dasch.domain.SipiTarget
import swiss.dasch.infrastructure.CorrelationId
import zio.*

/** The one mapping of ingest failures to the error body; a new failure type gets a case here. */
object IngestFailure {

  val internalErrorCode: String = "internal"

  private val internalMessage = "An internal error occurred. Report the correlation_id to DaSCH support."

  /** sipi's codes for an upload it cannot read: the user must fix the file. */
  private val uploadContentCodes =
    Set("decode_failed", "unsupported_format", "malformed_input", "metadata_parse_failed")

  private def isUploadContentError(e: SipiCliError): Boolean =
    e.target == SipiTarget.Upload && e.phase == Phase.Read && uploadContentCodes.contains(e.errorCode)

  /** A failure caused by the uploaded file, which the client must fix; every other failure is a server fault. */
  def isClientError(t: Throwable): Boolean = t match {
    case _: IngestRejected => true
    case e: SipiCliError   => isUploadContentError(e)
    case _                 => false
  }

  def errorCode(t: Throwable): String = t match {
    case IngestRejected(code, _) => code
    case e: SipiCliError         => e.errorCode
    case _                       => internalErrorCode
  }

  def toApiProblem(t: Throwable): URIO[Any, ApiProblem] =
    t match {
      case IngestRejected(code, message) =>
        CorrelationId.current.map(id => UnprocessableContent(id.value, code, message))
      case e: SipiCliError if isUploadContentError(e) =>
        ZIO.succeed(UnprocessableContent(e.correlationId, e.errorCode, e.clientMessage))
      case e: SipiCliError =>
        internalWith(CorrelationId(e.correlationId), e.errorCode, "Sipi failed", Cause.fail(t))
      case _ => internal("Ingest request failed", Cause.fail(t))
    }

  /**
   * Logs `cause` and builds the structured 500 with the same correlation ID.
   *
   * The log is annotated here too, because outside [[CorrelationId.scoped]] `current` gives a fresh ID that is not in
   * the annotations yet.
   */
  private[api] def internal(message: String, cause: Cause[Any]): UIO[InternalServerError] =
    CorrelationId.current.flatMap(internalWith(_, internalErrorCode, message, cause))

  private def internalWith(id: CorrelationId, code: String, message: String, cause: Cause[Any]) =
    ZIO
      .logAnnotate(CorrelationId.annotationKey, id.value)(ZIO.logErrorCause(message, cause))
      .as(InternalServerError(id.value, code, internalMessage))
}
