/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.*
import zio.stream.ZSink
import zio.stream.ZStream

import java.io.IOException
import scala.annotation.nowarn

import org.knora.bagit.BagIt
import org.knora.bagit.BagItError
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.store.triplestore.api.TriplestoreService

// This error is used to indicate that an import already exists.
final case class ImportExistsError(t: CurrentDataTask)
// This error is used to indicate that an import is already in progress.
final case class ImportInProgressError(t: CurrentDataTask)

@nowarn("msg=unused explicit parameter")
final class ProjectMigrationImportService(
  currentImport: DataTaskState,
  groupService: KnoraGroupService,
  projectService: KnoraProjectService,
  storage: ProjectMigrationStorageService,
  triplestore: TriplestoreService,
  userService: KnoraUserService,
) { self =>

  def importDataExport(
    projectIri: ProjectIri,
    createdBy: User,
    stream: ZStream[Any, Throwable, Byte],
  ): IO[ImportExistsError, CurrentDataTask] = for {
    importTask <-
      currentImport.makeNew(projectIri, createdBy).mapError { case StatesExistError(t) => ImportExistsError(t) }
    bagItPath <- storage.importBagItZipPath(importTask.id)
    _         <- stream.run(ZSink.fromFile(bagItPath.toFile)).orDie
    _         <- doImport(importTask.id, projectIri).forkDaemon
  } yield importTask

  private def doImport(taskId: DataTaskId, projectIri: ProjectIri): UIO[Unit] =
    ZIO.scoped {
      for {
        _   <- storage.tempImportScoped(taskId)
        zip <- storage.importBagItZipPath(taskId)
        _   <- ZIO.logInfo(s"$taskId: Starting import for project '$projectIri'")
        _   <- BagIt.readAndValidateZip(zip).mapError {
               case ex: IOException => ex: Throwable
               case err: BagItError => new RuntimeException(err.message)
             }
        _ <- ZIO.logInfo(s"$taskId: BagIt validation passed for project '$projectIri'")
        _ <- currentImport.complete(taskId).ignore
        _ <- ZIO.logInfo(s"$taskId: Import completed for project '$projectIri'")
      } yield ()
    }.logError.catchAll(e =>
      ZIO.logError(s"$taskId: Import failed for project '$projectIri' with error: ${e.getMessage}") *>
        currentImport.fail(taskId).ignore,
    )

  def getImportStatus(importId: DataTaskId): IO[Option[Nothing], CurrentDataTask] = currentImport.find(importId)

  def deleteImport(importId: DataTaskId): IO[Option[ImportInProgressError], Unit] =
    currentImport
      .deleteIfNotInProgress(importId)
      .mapError {
        case Some(StateInProgressError(s)) => Some(ImportInProgressError(s))
        case None                          => None
      }
      .unit
}

object ProjectMigrationImportService {
  val layer: URLayer[
    KnoraGroupService & KnoraProjectService & KnoraUserService & ProjectMigrationStorageService & TriplestoreService,
    ProjectMigrationImportService,
  ] = DataTaskPersistence.noop >>> DataTaskState.layer >>> ZLayer.derive[ProjectMigrationImportService]
}
