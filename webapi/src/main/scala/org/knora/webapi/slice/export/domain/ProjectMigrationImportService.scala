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
import org.knora.bagit.domain.Bag
import org.knora.webapi.KnoraBaseVersion
import org.knora.webapi.http.version.BuildInfo
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
        _      <- storage.tempImportScoped(taskId)
        zip    <- storage.importBagItZipPath(taskId)
        _      <- ZIO.logInfo(s"$taskId: Starting import for project '$projectIri'")
        result <- BagIt.readAndValidateZip(zip).mapError {
                    case ex: IOException => ex: Throwable
                    case err: BagItError => new RuntimeException(err.message)
                  }
        (bag, _) = result
        _       <- ZIO.logInfo(s"$taskId: BagIt validation passed for project '$projectIri'")
        _       <- validateVersionCompatibility(bag, taskId)
        _       <- ZIO.logInfo(s"$taskId: Version compatibility validated for project '$projectIri'")
        _       <- currentImport.complete(taskId).ignore
        _       <- ZIO.logInfo(s"$taskId: Import completed for project '$projectIri'")
      } yield ()
    }.logError.catchAll(e =>
      ZIO.logError(s"$taskId: Import failed for project '$projectIri' with error: ${e.getMessage}") *>
        currentImport.fail(taskId).ignore,
    )

  private def validateVersionCompatibility(bag: Bag, taskId: DataTaskId): Task[Unit] = {
    val fields = bag.bagInfo.fold(List.empty[(String, String)])(_.additionalFields)
    for {
      knoraBaseVersionStr <- getUniqueField(fields, "KnoraBase-Version")
      knoraBaseVersion    <- ZIO
                            .fromOption(knoraBaseVersionStr.toIntOption)
                            .orElseFail(
                              new RuntimeException(
                                s"KnoraBase-Version '$knoraBaseVersionStr' is not a valid integer",
                              ),
                            )
      _ <-
        ZIO.when(knoraBaseVersion != KnoraBaseVersion)(
          ZIO.fail(
            new RuntimeException(
              s"KnoraBase-Version mismatch: bag has version $knoraBaseVersion, but this instance requires version $KnoraBaseVersion",
            ),
          ),
        )
      dspApiVersion <- getUniqueField(fields, "Dsp-Api-Version")
      _             <-
        ZIO.when(dspApiVersion != BuildInfo.version)(
          ZIO.logWarning(
            s"$taskId: Dsp-Api-Version mismatch: bag has version '$dspApiVersion', this instance is version '${BuildInfo.version}'",
          ),
        )
    } yield ()
  }

  private def getUniqueField(fields: List[(String, String)], name: String): Task[String] =
    fields.filter(_._1 == name) match {
      case Nil          => ZIO.fail(new RuntimeException(s"Required field '$name' is missing from bag-info.txt"))
      case List((_, v)) => ZIO.succeed(v)
      case _            => ZIO.fail(new RuntimeException(s"Field '$name' appears more than once in bag-info.txt"))
    }

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
