/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiProblem.NotFound
import swiss.dasch.domain.AssetRef
import swiss.dasch.domain.ProjectShortcode
import swiss.dasch.infrastructure.CorrelationId
import zio.*

trait HandlerFunctions {
  def projectNotFoundOrServerError(mayBeError: Option[Throwable], shortcode: ProjectShortcode): UIO[ApiProblem] =
    mayBeError.fold(ZIO.succeed(NotFound(shortcode)))(IngestFailure.toApiProblem)

  def assetRefNotFoundOrServerError(mayBeError: Option[Throwable], assetRef: AssetRef): UIO[ApiProblem] =
    mayBeError.fold(ZIO.succeed(NotFound(s"${assetRef.belongsToProject}/${assetRef.id}", "AssetRef")))(
      IngestFailure.toApiProblem,
    )
}

object HandlerFunctions {

  /**
   * Wraps the logic of an ingest route inside `serverLogic`.
   *
   * It must stay inside `serverLogic`: ztapir turns a defect into a failure after the logic returns, where it can no
   * longer be told apart from a typed failure, and the client would get tapir's plain-text 500 without a log entry.
   */
  def guarded[A](z: IO[ApiProblem, A]): IO[ApiProblem, A] =
    CorrelationId.scoped {
      z.catchAllCause { cause =>
        cause.failureOption match {
          case Some(problem) if cause.defects.isEmpty => ZIO.fail(problem)
          case Some(problem)                          =>
            IngestFailure.internal("Ingest request failed with a defect", cause) *> ZIO.fail(problem)
          case None if cause.isInterruptedOnly => ZIO.failCause(cause)
          case None                            => IngestFailure.internal("Ingest request died", cause).flip
        }
      }
    }
}
