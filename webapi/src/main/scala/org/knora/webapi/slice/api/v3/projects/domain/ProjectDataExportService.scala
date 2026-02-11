/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects.domain

import zio.*
import zio.IO
import zio.ZLayer
import zio.stream.ZStream

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User

// This error is used to indicate that an export is already in progress
final case class ExportExistsError(value: CurrentDataTask)

// This error is used to indicate that an export is still in progress
final case class ExportInProgressError(value: CurrentDataTask)

// This error is used to indicate that an export has failed
final case class ExportFailedError(value: CurrentDataTask)

final class ProjectDataExportService(currentExp: DataTaskState) { self =>

  def createExport(projectIri: ProjectIri, createdBy: User): IO[ExportExistsError, CurrentDataTask] =
    for {
      curExp <- currentExp.makeNew(projectIri, createdBy).mapError { case StateExistError(t) => ExportExistsError(t) }
      _      <-
        /// Simulate a long-running export process by completing the export after a delay.
        // In a real implementation, this would be where the actual export logic goes.
        currentExp.complete(curExp.id).delay(10.seconds).ignore.forkDaemon
    } yield curExp

  /**
   * Delete the export task with the given id if it exists and is not in progress.
   * An export can only be deleted if it is in a completed or failed state. If the export is in progress, an error is returned.
   *
   * @param exportId the id of the export task to delete
   * @return An IO that fails with None if the export task is not found.
   *         An IO that fails with ExportInProgressError if the export task is found but is still in progress.
   *         An IO that succeeds with Unit if the export task was successfully deleted.
   */
  def deleteExport(exportId: DataTaskId): IO[Option[ExportInProgressError], Unit] =
    currentExp
      .deleteIfNotInProgress(exportId)
      .mapError {
        case Some(StateInProgressError(s)) => Some(ExportInProgressError(s))
        case None                          => None
      }
      .unit

  def getExportStatus(exportId: DataTaskId): IO[Option[Nothing], CurrentDataTask] = currentExp.find(exportId)

  /**
   * Download the export file for the given export task id if it exists and is completed.
   * An export can only be downloaded if it is in a completed state.
   * @param exportId the id of the export task to download
   * @return An IO that fails with None if the export task is not found.
   *         An IO that fails with ExportInProgressError if the export task is found but is still in progress.
   *         An IO that fails with ExportFailedError if the export task is found but has failed.
   *         An IO that succeeds with a tuple of the file name and a stream of bytes representing the export.
   */
  def downloadExport(
    exportId: DataTaskId,
  ): IO[Option[ExportInProgressError | ExportFailedError], (String, ZStream[Any, Throwable, Byte])] =
    for {
      exp <- canDownloadExport(exportId)
      // Simulate a file download by returning a stream of bytes.
      // In a real implementation, this would be where the actual file streaming logic goes.
      fileName    = s"export-${exp.id}.zip"
      fileContent = ZStream.empty
    } yield (fileName, fileContent)

  private def canDownloadExport(
    exportId: DataTaskId,
  ): IO[Option[ExportInProgressError | ExportFailedError], CurrentDataTask] =
    currentExp.find(exportId).flatMap {
      case exp if exp.isInProgress => ZIO.fail(Some(ExportInProgressError(exp)))
      case exp if exp.isFailed     => ZIO.fail(Some(ExportFailedError(exp)))
      case exp                     => ZIO.succeed(exp)
    }
}

object ProjectDataExportService {
  val layer: ZLayer[Any, Nothing, ProjectDataExportService] =
    DataTaskState.layer >>> ZLayer.derive[ProjectDataExportService]
}
