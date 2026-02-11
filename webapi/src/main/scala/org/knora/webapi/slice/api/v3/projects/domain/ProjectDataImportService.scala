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
  currentImport: Ref[Option[CurrentDataTask]],
) { self =>

  def importDataExport(
    projectIri: ProjectIri,
    createdBy: User,
    stream: ZStream[Any, Throwable, Byte],
  ): IO[ImportInProgressError, CurrentDataTask] = for {
    existingImport <- self.currentImport.get
    curExp         <- existingImport match {
                case Some(exp) if exp.isInProgress => ZIO.fail(ImportInProgressError(exp))
                case _                             =>
                  CurrentDataTask
                    .makeNew(projectIri, createdBy)
                    .tap(cde => self.currentImport.set(Some(cde)))
              }
    // In a real implementation, we would process the stream here and update the import status accordingly.
    _ <- stream.runDrain.orDie
    _ <- (
           /// Simulate a long-running export process by completing the export after a delay.
           // In a real implementation, this would be where the actual export logic goes.
           self.currentImport.getAndUpdateSome { case Some(exp) => Some(exp.complete()) }.delay(10.seconds)
         ).forkDaemon
  } yield curExp

  def getImportStatus(importId: DataTaskId): IO[Option[Nothing], CurrentDataTask] =
    self.currentImport.get.flatMap(ZIO.fromOption).filterOrFail(_.id == importId)(None)
}

object ProjectDataImportService {
  val layer = ZLayer.fromZIO(Ref.make[Option[CurrentDataTask]](None)) >>> ZLayer.derive[ProjectDataImportService]
}
