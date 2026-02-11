/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects.domain

import zio.*
import zio.IO
import zio.Ref
import zio.ZLayer
import zio.stream.ZStream
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User

// This error is used to indicate that an export is already in progress
// when trying to create a new export.
case class ExportExistsError(value: CurrentDataTask)

// This error is used to indicate that an export is already in progress
// when trying to create a new export.
case class ExportFailedError(value: CurrentDataTask)

final class ProjectDataExportService(currentExp: Ref[Option[CurrentDataTask]]) { self =>

  def createExport(projectIri: ProjectIri, createdBy: User): IO[ExportExistsError, CurrentDataTask] =
    for {
      curExp <- startNewExport(projectIri, createdBy)
      _      <-
        /// Simulate a long-running export process by completing the export after a delay.
        // In a real implementation, this would be where the actual export logic goes.
        completeExport(curExp.id).delay(10.seconds).ignore.forkDaemon
    } yield curExp

  def startNewExport(projectIri: ProjectIri, createdBy: User): IO[ExportExistsError, CurrentDataTask] =
    for {
      // prepare a new data task, but do not set it as the current export yet
      newExp <- CurrentDataTask.makeNew(projectIri, createdBy)
      // need to use .modify here to ensure that the check for an existing export and the setting of the new export
      // are atomic, first tuple is return value, second tuple is new state
      exp <- self.currentExp.modify {
               case Some(exp) => (ZIO.fail(ExportExistsError(exp)), Some(exp))
               case _         => (ZIO.succeed(newExp), Some(newExp))
             }.flatten
    } yield exp

  def completeExport(exportId: DataTaskId): IO[Option[Nothing], Unit] =
    findExport(exportId)
      .filterOrFail(exp => exp.id == exportId && exp.isInProgress)(None)
      .tap(exp => setCurrentExp(exp.complete()))
      .unit

  def findExport(exportId: DataTaskId): IO[Option[Nothing], CurrentDataTask] =
    self.currentExp.get.flatMap {
      case Some(exp) if exp.id == exportId => ZIO.succeed(exp)
      case _                               => ZIO.fail(None)
    }

  private def setCurrentExp(exp: CurrentDataTask): UIO[Unit] = self.currentExp.set(Some(exp))
  private def deleteCurrentExp(): UIO[Unit]                  = self.currentExp.set(None)

  // Delete the export in question.
  // Do not delete the export if it is still in progress.
  // Will delete the export if it has completed or failed.
  // Return:
  // * ZIO.fail(None) - if the export was not found
  // * ZIO.fail(Some(ExportExistsError)) - if the export is still in progress
  // * ZIO.unit - if the export was successfully deleted,
  def deleteExport(exportId: DataTaskId): IO[Option[ExportExistsError], Unit] =
    canDeleteExport(exportId) *> deleteCurrentExp()

  def canDeleteExport(exportId: DataTaskId): IO[Option[ExportExistsError], Unit] =
    findExport(exportId)
      .flatMap(exp => ZIO.fail(Some(ExportExistsError(exp))).when(exp.isInProgress))
      .unit

  def getExportStatus(exportId: DataTaskId): IO[Option[Nothing], CurrentDataTask] = findExport(exportId)

  def downloadExport(
    exportId: DataTaskId,
  ): IO[Option[ExportExistsError | ExportFailedError], (String, ZStream[Any, Throwable, Byte])] =
    for {
      exp <- canDownloadExport(exportId)
      // Simulate a file download by returning a stream of bytes.
      // In a real implementation, this would be where the actual file streaming logic goes.
      fileName    = s"export-${exp.id}.zip"
      fileContent = ZStream.empty
    } yield (fileName, fileContent)

  def canDownloadExport(exportId: DataTaskId): IO[Option[ExportExistsError | ExportFailedError], CurrentDataTask] =
    findExport(exportId).flatMap {
      case exp if exp.isInProgress => ZIO.fail(Some(ExportExistsError(exp)))
      case exp if exp.isFailed     => ZIO.fail(Some(ExportFailedError(exp)))
      case exp                     => ZIO.succeed(exp)
    }
}

object ProjectDataExportService {
  val layer: ZLayer[Any, Nothing, ProjectDataExportService] =
    ZLayer.fromZIO(Ref.make[Option[CurrentDataTask]](None)) >>> ZLayer.derive[ProjectDataExportService]
}
