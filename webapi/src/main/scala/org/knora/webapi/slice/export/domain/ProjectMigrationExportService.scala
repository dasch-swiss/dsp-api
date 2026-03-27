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
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.version.BuildInfo
import org.apache.jena.query.DatasetFactory
import org.apache.jena.riot.Lang as JenaLang
import org.apache.jena.riot.RDFDataMgr

import org.knora.webapi.messages.util.rdf.NQuads
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.AdminConstants.permissionsDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.DspIngestClient
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

// This error is used to indicate that an export exists
final case class ExportExistsError(t: CurrentDataTask)

// This error is used to indicate that an export is still in progress
final case class ExportInProgressError(t: CurrentDataTask)

// This error is used to indicate that an export has failed
final case class ExportFailedError(t: CurrentDataTask)

final class ProjectMigrationExportService(
  currentExp: DataTaskState,
  dspIngestClient: DspIngestClient,
  projectService: KnoraProjectService,
  storage: ProjectMigrationStorageService,
  triplestore: TriplestoreService,
) extends QueryBuilderHelper { self =>

  def createExport(
    project: KnoraProject,
    createdBy: User,
    skipAssets: Boolean = false,
  ): IO[ExportExistsError, CurrentDataTask] = for {
    curExp <- currentExp.makeNew(project.id, createdBy).mapError { case StatesExistError(t) => ExportExistsError(t) }
    _      <- ZIO.logInfo(s"$curExp: Created export task for project '${project.id}' by user '${createdBy.id}'")
    _      <- doExport(project, curExp, skipAssets).forkDaemon
  } yield curExp

  private def doExport(project: KnoraProject, task: CurrentDataTask, skipAssets: Boolean): UIO[Unit] =
    ZIO.scoped {
      for {
        tempExport            <- storage.tempExportScoped(task.id)
        (tempPath, exportPath) = tempExport
        _                     <- ZIO.logInfo(s"${task.id}: Exporting data for project '${project.id}' to '$exportPath'")
        rdfPath                = tempPath / "rdf"
        _                     <- Files.createDirectories(rdfPath)
        assetZipPath           = tempPath / "assets" / "assets.zip"
        collectRdf             = collectOntologyGraphs(task.id, project, rdfPath) <&>
                       collectProjectDataGraph(task.id, project, rdfPath) <&>
                       collectAdminGraphData(task.id, project, rdfPath) <&>
                       collectPermissionsGraphData(task.id, project, rdfPath)
        assetResult <- collectRdf.zipParRight(exportAssets(task.id, project.shortcode, assetZipPath, skipAssets))
        _           <- createBagItZip(task.id, rdfPath, assetResult, project.id)
        _           <- currentExp.complete(task.id).ignore
        _           <- ZIO.logInfo(s"${task.id}: Export completed for project ${project.id} to $exportPath")
      } yield ()
    }.logError.catchAll(e =>
      ZIO.logError(s"${task.id}: Export failed for project ${project.id} with error: ${e.getMessage}") *>
        currentExp.fail(task.id).ignore,
    )

  private def exportAssets(
    taskId: DataTaskId,
    shortcode: KnoraProject.Shortcode,
    targetPath: Path,
    skipAssets: Boolean,
  ): Task[Option[Path]] =
    if (skipAssets) ZIO.none
    else
      for {
        _      <- ZIO.logInfo(s"$taskId: Exporting assets for project '$shortcode' from ingest")
        _      <- Files.createDirectories(targetPath.parent.get)
        result <- dspIngestClient.exportProject(shortcode, targetPath)
        _      <- result match {
               case Some(_) => ZIO.logInfo(s"$taskId: Assets exported to '$targetPath'")
               case None    =>
                 ZIO.logWarning(
                   s"$taskId: No assets found for project '$shortcode' on ingest, continuing without assets",
                 )
             }
      } yield result

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

      // Step 1: Find all user IRIs referenced by attachedToUser in the project's data graph
      dataGraph        = projectService.getDataGraphForProject(project)
      referencedResult <- triplestore.select(ReferencedUserIrisQuery.build(dataGraph))
      referencedIris    = referencedResult.getCol("user").flatMap(UserIri.from(_).toOption).toSet
      _ <- ZIO.when(referencedIris.nonEmpty)(
             ZIO.logInfo(s"$taskId: Found ${referencedIris.size} users referenced by attachedToUser in data graph"),
           )

      // Step 2: Build and execute the CONSTRUCT query (with referenced users if any)
      queryStr = AdminDataQuery.buildWithReferencedUsers(project.id, referencedIris)
      rdfStr  <- triplestore.queryRdf(Construct(queryStr))

      // Step 3: Parse result into Jena model, scope memberships, write as NQuads
      _ <- ZIO.attempt {
             val model = org.apache.jena.rdf.model.ModelFactory.createDefaultModel()
             RDFDataMgr.read(model, java.io.ByteArrayInputStream(rdfStr.getBytes(java.nio.charset.StandardCharsets.UTF_8)), JenaLang.TURTLE)
             AdminModelScoping.removeNonProjectMemberships(model, project.id.value)
             val dataset = DatasetFactory.create()
             dataset.addNamedModel(adminDataNamedGraph.value, model)
             val out = java.io.FileOutputStream(adminFile.toFile)
             try RDFDataMgr.write(out, dataset, JenaLang.NQUADS)
             finally out.close()
             dataset.close()
           }
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

  private def createBagItZip(
    taskId: DataTaskId,
    rdfPath: Path,
    assetZipPath: Option[Path],
    projectIri: ProjectIri,
  ) =
    for {
      zipFile      <- storage.exportBagItZipPath(taskId)
      externalHost <- AppConfig.knoraApi(_.externalHost)
      _            <- ZIO.logInfo(s"$taskId: Writing export $zipFile")
      assetEntries  = assetZipPath.map(p => PayloadEntry.File("assets/assets.zip", p)).toList
      _            <- BagIt.create(
             PayloadEntry.Directory("rdf", rdfPath) :: assetEntries,
             zipFile,
             bagInfo = Some(
               BagInfo(
                 baggingDate = Some(java.time.LocalDate.now()),
                 sourceOrganization = Some("DaSCH Service Platform"),
                 externalIdentifier = Some(projectIri.value),
                 additionalFields = List(
                   ("KnoraBase-Version", s"$KnoraBaseVersion"),
                   ("Dsp-Api-Version", BuildInfo.version),
                   ("Source-Server", externalHost),
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
    dir <- storage.exportDir(exportId).tap(Files.deleteRecursive(_).logError.orDie)
    _   <- ZIO.logInfo(s"$exportId: Cleaned export dir $dir")
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
      zipFile <- storage.exportBagItZipPath(exportId)
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
    DspIngestClient & KnoraProjectService & ProjectMigrationStorageService & TriplestoreService,
    ProjectMigrationExportService,
  ] = FilesystemDataTaskPersistence.exportLayer >>> ZLayer.derive[ProjectMigrationExportService]
}
