/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects.domain
import zio.*
import zio.stream.ZStream

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User

// This error is used to indicate that an import is already in progress for a project when a new import request is made.
final case class ImportInProgressError(value: CurrentDataTask)

final class ProjectDataImportService(
  currentImport: DataTaskState,
) { self =>

  def importDataExport(
    projectIri: ProjectIri,
    createdBy: User,
    stream: ZStream[Any, Throwable, Byte],
  ): IO[ImportInProgressError, CurrentDataTask] = for {
    importTask <-
      currentImport.makeNew(projectIri, createdBy).mapError { case StateExist(t) => ImportInProgressError(t) }
    // In a real implementation, we would process the stream here and update the import status accordingly.
    _ <- stream.runDrain.orDie
    _ <- (
           /// Simulate a long-running export process by completing the export after a delay.
           // In a real implementation, this would be where the actual export logic goes.
           currentImport.complete(importTask.id).delay(10.seconds).ignore
         ).forkDaemon
  } yield importTask

  def getImportStatus(importId: DataTaskId): IO[Option[Nothing], CurrentDataTask] = currentImport.find(importId)

  def deleteImport(importId: DataTaskId): IO[Option[ImportInProgressError], Unit] =
    currentImport
      .atomicFindAndUpdate[ImportInProgressError](
        importId,
        {
          case imp if imp.isInProgress => Left(ImportInProgressError(imp))
          case _                       => Right(None)
        },
      )
      .unit
}

object ProjectDataImportService {
  val layer = DataTaskState.layer >>> ZLayer.derive[ProjectDataImportService]
}
