/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.*
import zio.nio.file.Files
import zio.nio.file.Path
import zio.stream.ZStream

import org.knora.bagit.BagIt
import org.knora.bagit.domain.BagInfo
import org.knora.bagit.domain.PayloadEntry
import org.knora.webapi.KnoraBaseVersion
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.messages.util.rdf.NQuads
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.AdminConstants.permissionsDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService

// This error is used to indicate that an export exists
final case class ExportExistsError(t: CurrentDataTask)

// This error is used to indicate that an export is still in progress
final case class ExportInProgressError(t: CurrentDataTask)

// This error is used to indicate that an export has failed
final case class ExportFailedError(t: CurrentDataTask)

final class ProjectMigrationExportService(
  apiConfig: KnoraApi,
  currentExp: DataTaskState,
  projectService: KnoraProjectService,
  storage: ProjectMigrationStorageService,
  triplestore: TriplestoreService,
) extends QueryBuilderHelper { self =>

  def createExport(project: KnoraProject, createdBy: User): IO[ExportExistsError, CurrentDataTask] = for {
    curExp <- currentExp.makeNew(project.id, createdBy).mapError { case StatesExistError(t) => ExportExistsError(t) }
    _      <- ZIO.logInfo(s"$curExp: Created export task for project '${project.id}' by user '${createdBy.id}'")
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
        _                     <- collectOntologyGraphs(task.id, project, rdfPath) <&>
               collectProjectDataGraph(task.id, project, rdfPath) <&>
               collectAdminGraphData(task.id, project, rdfPath) <&>
               collectPermissionsGraphData(task.id, project, rdfPath)
        _ <- createBagItZip(task.id, rdfPath, project.id)
        _ <- currentExp.complete(task.id).ignore
        _ <- ZIO.logInfo(s"${task.id}: Export completed for project ${project.id} to $exportPath")
      } yield ()
    }.logError.catchAll(e =>
      ZIO.logError(s"${task.id}: Export failed for project ${project.id} with error: ${e.getMessage}") *>
        currentExp.fail(task.id).ignore,
    )

  private def collectOntologyGraphs(taskId: DataTaskId, project: KnoraProject, rdfPath: Path) = for {
    graphs <- projectService.getOntologyGraphsForProject(project)
    _      <- ZIO.logInfo(s"$taskId: Collecting ontologies '${graphs.map(_.value).mkString(",")}'")
    _      <- ZIO.foreachParDiscard(graphs.zipWithIndex)((g, i) => downloadGraph(g, rdfPath / s"ontology-${i + 1}.nq"))
  } yield ()

  private def downloadGraph(graph: InternalIri, file: Path) =
    Files.createFile(file) *> triplestore.downloadGraph(graph, file, NQuads)

  private def collectProjectDataGraph(taskId: DataTaskId, project: KnoraProject, rdfPath: Path) =
    val dataGraph = projectService.getDataGraphForProject(project)
    for {
      _ <- ZIO.logInfo(s"$taskId: Collecting project data from graph '${dataGraph.value}'")
      _ <- downloadGraph(dataGraph, rdfPath / "data.nq")
    } yield ()

  private def collectAdminGraphData(taskId: DataTaskId, project: KnoraProject, rdfPath: Path) =
    for {
      _        <- ZIO.logInfo(s"$taskId: Collecting project admin data from graph '${adminDataNamedGraph.value}'")
      adminFile = rdfPath / "admin.nq"
      _        <- Files.createFile(adminFile) *>
             triplestore.queryToFile(AdminDataQuery.build(project.id), adminDataNamedGraph, adminFile, NQuads)
    } yield ()

  private def collectPermissionsGraphData(taskId: DataTaskId, project: KnoraProject, rdfPath: Path) =
    for {
      _             <- ZIO.logInfo(s"$taskId: Collecting project permission data from graph '${permissionsDataNamedGraph.value}'")
      permissionFile = rdfPath / "permission.nq"
      query          = PermissionDataQuery.build(project.id)
      _             <- ZIO.logDebug(s"$taskId: Permission data query: \n\n${query.getQueryString}")
      _             <- Files.createFile(permissionFile) *>
             triplestore.queryToFile(query, permissionsDataNamedGraph, permissionFile, NQuads)
    } yield ()

  private def createBagItZip(taskId: DataTaskId, rdfPath: Path, projectIri: ProjectIri) =
    for {
      zipFile <- storage.bagItZipPath(taskId)
      _       <- ZIO.logInfo(s"$taskId: Writing export $zipFile")
      _       <- BagIt.create(
             List(PayloadEntry.Directory("rdf", rdfPath)),
             zipFile,
             bagInfo = Some(
               BagInfo(
                 baggingDate = Some(java.time.LocalDate.now()),
                 sourceOrganization = Some("DaSCH Service Platform"),
                 externalIdentifier = Some(projectIri.value),
                 additionalFields = List(
                   ("KnoraBase-Version", s"$KnoraBaseVersion"),
                   ("Dsp-Api-Version", BuildInfo.version),
                   ("Source-Server", apiConfig.externalHost),
                 ),
               ),
             ),
           )
    } yield ()

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
    zipFile <- storage.bagItZipPath(exportId)
    _       <- Files.deleteIfExists(zipFile).logError.orDie
    _       <- ZIO.logInfo(s"$exportId: Deleted export task and associated export file")
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
      exp     <- canDownloadExport(exportId)
      zipFile <- storage.bagItZipPath(exportId)
      _       <- Files
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

object ProjectMigrationExportService {
  val layer: URLayer[
    KnoraApi & KnoraProjectService & ProjectMigrationStorageService & TriplestoreService,
    ProjectMigrationExportService,
  ] = FilesystemDataTaskPersistence.exportLayer >>> ZLayer {
    for {
      // Restore the current export task from the filesystem if it exists,
      // and mark it as failed if it was in progress
      fsPersistence <- ZIO.service[FilesystemDataTaskPersistence]
      restored      <- fsPersistence.restore()
      wasInProgress  = restored.exists(_.isInProgress)
      corrected      = restored.map { task =>
                    if (task.isInProgress) task.fail().getOrElse(task) else task
                  }
      _ <- ZIO.when(wasInProgress && corrected.isDefined) {
             val task = corrected.get
             ZIO.logWarning(s"${task.id}: Marking previously in-progress export as failed due to service restart") *>
               fsPersistence.onChanged(task)
           }
      ref            <- Ref.make(corrected)
      state           = new DataTaskState(ref, fsPersistence)
      apiConfig      <- ZIO.service[KnoraApi]
      projectService <- ZIO.service[KnoraProjectService]
      storage        <- ZIO.service[ProjectMigrationStorageService]
      triplestore    <- ZIO.service[TriplestoreService]
    } yield new ProjectMigrationExportService(apiConfig, state, projectService, storage, triplestore)
  }
}
