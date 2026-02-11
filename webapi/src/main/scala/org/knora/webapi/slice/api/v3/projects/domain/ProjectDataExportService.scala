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
case class ExportExistsError(value: CurrentDataTask)

// This error is used to indicate that an export is still in progress
case class ExportInProgressError(value: CurrentDataTask)

// This error is used to indicate that an export is already in progress
case class ExportFailedError(value: CurrentDataTask)

final class ProjectDataExportService(currentExp: DataTaskState) { self =>

  def createExport(projectIri: ProjectIri, createdBy: User): IO[ExportExistsError, CurrentDataTask] =
    for {
      curExp <- currentExp.makeNew(projectIri, createdBy).mapError { case StateExistError(t) => ExportExistsError(t) }
      _      <-
        /// Simulate a long-running export process by completing the export after a delay.
        // In a real implementation, this would be where the actual export logic goes.
        currentExp.complete(curExp.id).delay(10.seconds).ignore.forkDaemon
    } yield curExp

  // Delete the export in question.
  // Do not delete the export if it is still in progress.
  // Will delete the export if it has completed or failed.
  // Return:
  // * ZIO.fail(None) - if the export was not found
  // * ZIO.fail(Some(ExportExistsError)) - if the export is still in progress
  // * ZIO.unit - if the export was successfully deleted,
  def deleteExport(exportId: DataTaskId): IO[Option[ExportInProgressError], Unit] =
    currentExp
      .deleteIfNotInProgress(exportId)
      .mapError {
        case Some(StateInProgressError(s)) => Some(ExportInProgressError(s))
        case None                          => None
      }
      .unit

  def getExportStatus(exportId: DataTaskId): IO[Option[Nothing], CurrentDataTask] = currentExp.find(exportId)

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

  def canDownloadExport(exportId: DataTaskId): IO[Option[ExportInProgressError | ExportFailedError], CurrentDataTask] =
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
