/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.knora.bagit.BagIt
import org.knora.bagit.domain.BagInfo
import org.knora.bagit.domain.PayloadEntry
import org.knora.webapi.KnoraBaseVersion
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.messages.util.rdf.NQuads
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.http.version.BuildInfo
import zio.*
import zio.stream.ZStream
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import zio.nio.file.Files

// This error is used to indicate that an export exists
final case class ExportExistsError(t: CurrentDataTask)

// This error is used to indicate that an export is still in progress
final case class ExportInProgressError(t: CurrentDataTask)

// This error is used to indicate that an export has failed
final case class ExportFailedError(t: CurrentDataTask)

final class ProjectDataExportService(
  apiConfig: KnoraApi,
  currentExp: DataTaskState,
  projectService: KnoraProjectService,
  storage: ProjectDataExportStorage,
  triplestore: TriplestoreService,
) { self =>

  def createExport(project: KnoraProject, createdBy: User): IO[ExportExistsError, CurrentDataTask] =
    for {
      curExp <- currentExp.makeNew(project.id, createdBy).mapError { case StatesExistError(t) => ExportExistsError(t) }
      _      <- ZIO.logInfo(s"Created export task '${curExp.id}' for project '${project.id}' by user '${createdBy.id}'")
      _      <- doExport(project, curExp).forkDaemon
    } yield curExp

  private def doExport(project: KnoraProject, task: CurrentDataTask): UIO[Unit] =
    ZIO.scoped {
      for {
        tempExport            <- storage.tempExportScoped(task.id)
        (tempPath, exportPath) = tempExport
        _                     <- ZIO.logInfo(s"${task.id}: Exporting data for project '${project.id}' to '$exportPath'")
        rdfPath                = tempPath / "rdf"
        _                     <- Files.createDirectories(rdfPath)

        // collect project ontologies
        graphs <- projectService.getOntologyGraphsForProject(project)
        _      <- ZIO.logInfo(s"${task.id}: Collecting ontologies '${graphs.map(_.value).mkString(",")}'")
        _      <- ZIO.foreachDiscard(graphs.zipWithIndex) { (g, i) =>
               val file = rdfPath / s"ontology-${i + 1}.nq"
               Files.createFile(file) *> triplestore.downloadGraph(g, file, NQuads)
             }

        // collect project data
        dataGraph = projectService.getDataGraphForProject(project)
        _        <- ZIO.logInfo(s"${task.id}: Collecting project data from graph '${dataGraph.value}'")
        dataFile  = rdfPath / "data.nq"
        _        <- Files.createFile(dataFile) *> triplestore.downloadGraph(dataGraph, dataFile, NQuads)

        // create bagit zip
        _      <- ZIO.logInfo(s"${task.id}: Writing export bagit.zip")
        zipFile = storage.bagItZipPath(task.id)
        _      <- BagIt.create(
               List(PayloadEntry.Directory("rdf", rdfPath)),
               zipFile,
               bagInfo = Some(
                 BagInfo(
                   baggingDate = Some(java.time.LocalDate.now()),
                   sourceOrganization = Some("DaSCH Service Platform"),
                   externalIdentifier = Some(project.id.value),
                   additionalFields = List(
                     ("KnoraBase-Version", s"$KnoraBaseVersion"),
                     ("Dsp-Api-Version", BuildInfo.version),
                     ("Source-Server", apiConfig.externalHost),
                   ),
                 ),
               ),
             )
        _ <- currentExp.complete(task.id).ignore
        _ <- ZIO.logInfo(s"Export completed for project ${project.id} to $exportPath")
      } yield ()
    }.logError.catchAll(e =>
      ZIO.logError(s"Export failed for project ${project.id} with error: ${e.getMessage}") *>
        currentExp.fail(task.id).ignore,
    )

  /**
   * Delete the export task with the given id if it exists and is not in progress.
   * An export can only be deleted if it is in a completed or failed state. If the export is in progress, an error is returned.
   *
   * @param exportId the id of the export task to delete
   * @return An IO that fails with None if the export task is not found.
   *         An IO that fails with ExportInProgressError if the export task is found but is still in progress.
   *         An IO that succeeds with Unit if the export task was successfully deleted.
   */
  def deleteExport(exportId: DataTaskId): IO[Option[ExportInProgressError], Unit] = for {
    _ <- currentExp
           .deleteIfNotInProgress(exportId)
           .mapError {
             case Some(StateInProgressError(s)) => Some(ExportInProgressError(s))
             case None                          => None
           }
    zipFile = storage.bagItZipPath(exportId)
    _      <- Files.deleteIfExists(zipFile).logError.orDie
    _      <- ZIO.logInfo(s"Deleted export task with id $exportId and associated export file")
  } yield ()

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
      exp    <- canDownloadExport(exportId)
      zipFile = storage.bagItZipPath(exportId)
      _      <- Files
             .exists(zipFile)
             .filterOrDie(_ == true)(new RuntimeException(s"Export file not found for export id $exportId"))
      fileName = s"migration-export-${exp.id}.zip"
      content  = ZStream.fromFile(zipFile.toFile)
    } yield (fileName, content)

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
  val layer = DataTaskState.layer >>> ZLayer.derive[ProjectDataExportService]
}
