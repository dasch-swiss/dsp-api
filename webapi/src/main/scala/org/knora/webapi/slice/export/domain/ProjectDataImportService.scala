/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.*
import zio.nio.file.Files
import zio.nio.file.Path
import zio.stream.ZSink
import zio.stream.ZStream

import org.knora.webapi.messages.util.rdf.NQuads
import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.ontology.ConversionContext
import org.knora.webapi.slice.ontology.OntologyTransformer
import org.knora.webapi.store.triplestore.api.TriplestoreService

/**
 * Imports a project's data graph from a knora-api JSON-LD payload: transforms it to knora-base NQuads (assigned to
 * the project's data named graph), SHACL-validates the result against the project's ontologies fetched from the
 * triplestore, and streams it into the triplestore. Create-only: the project's data graph must not exist yet.
 */
final class ProjectDataImportService(
  state: DataTaskState,
  storage: ProjectDataImportStorageService,
  projectService: KnoraProjectService,
  permissionsResponder: PermissionsResponder,
  transformer: OntologyTransformer,
  validator: ProjectMigrationImportValidator,
  triplestore: TriplestoreService,
) { self =>

  /** Whether the project's data named graph already contains any triples (create-only precondition, R7). */
  def dataGraphExists(project: KnoraProject): Task[Boolean] =
    triplestore.query(ProjectDataGraphExistsQuery.build(project))

  def importDataGraph(
    project: KnoraProject,
    createdBy: User,
    stream: ZStream[Any, Throwable, Byte],
  ): IO[ImportExistsError, CurrentDataTask] = for {
    task <- state.makeNew(project.id, createdBy).mapError { case StatesExistError(t) => ImportExistsError(t) }
    _    <- saveJsonLdStream(task.id, stream)
    _    <- doImport(task.id, project, createdBy).whenZIO(state.isInProgress(task.id)).forkDaemon
  } yield task

  private def saveJsonLdStream(taskId: DataTaskId, stream: ZStream[Any, Throwable, Byte]) = (
    for {
      _          <- storage.dataImportDir(taskId)
      jsonLdPath <- storage.dataImportJsonLdPath(taskId)
      _          <- stream.run(ZSink.fromFile(jsonLdPath.toFile))
    } yield ()
  ).tapError(e => state.fail(taskId, Option(e.getMessage).getOrElse(e.getClass.getSimpleName)).ignore).orDie

  private def doImport(taskId: DataTaskId, project: KnoraProject, createdBy: User): UIO[Unit] =
    ZIO.scoped {
      for {
        _          <- ZIO.logInfo(s"$taskId: Starting data import for project '${project.id}'")
        jsonLdPath <- storage.dataImportJsonLdPath(taskId)

        permissions <- permissionsResponder.newDataImportDefaultObjectAccessPermissions(project.id, createdBy)
        _           <- ZIO.logInfo(s"$taskId: Using permissions '$permissions' for project '${project.id}'")

        ctx          = ConversionContext(createdBy.userIri, project, permissions)
        transformed <- transformer
                         .toKnoraBase(jsonLdPath.toFile.toPath, ctx)
                         .mapError(e => new RuntimeException(s"Transformation failed: ${e.message}"))
        _ <- ZIO.logInfo(s"$taskId: Transformed data to knora-base for project '${project.id}'")

        _ <- validate(taskId, project, Path.fromJava(transformed))
        _ <- ZIO.logInfo(s"$taskId: Validation passed for project '${project.id}'")

        // Re-verify the create-only precondition immediately before the upload: a data graph may have appeared
        // since the synchronous check in the RestService (e.g. via a concurrent migration import). This narrows but
        // does not fully close the race window — acceptable for an admin-only, single-in-flight-task operation.
        // Wording kept aligned with `V3ErrorCode.data_graph_exists`.
        _ <- ZIO
               .fail(new RuntimeException(s"The data graph for project '${project.id}' already exists."))
               .whenZIO(dataGraphExists(project))

        _ <- ZIO.logInfo(s"$taskId: Uploading data to triplestore for project '${project.id}'")
        _ <- uploadToTriplestore(Path.fromJava(transformed))
        _ <- state.complete(taskId).ignore
        _ <- ZIO.logInfo(s"$taskId: Data import completed for project '${project.id}'")
      } yield ()
    }.catchAll(e =>
      ZIO.logError(s"$taskId: Data import failed for project '${project.id}' with error: ${e.getMessage}") *>
        state.fail(taskId, Option(e.getMessage).getOrElse(e.getClass.getSimpleName)).ignore,
    )

  /**
   * SHACL-validates the transformed NQuads, adapting the migration import's validation: the project's ontology
   * graphs are fetched from the triplestore — the payload carries instance data only. The data shapes also check
   * `attachedToUser` references against `knora-admin:User` instances, so a projection of the user typing triples
   * is included in the data files (only the typing triples — downloading the full admin graph would scale with
   * instance size, not import size). Including it in the data chunk means the shared validator's placeholder scan
   * also runs over it, which is harmless for typing-only triples.
   */
  private def validate(taskId: DataTaskId, project: KnoraProject, dataFile: Path): ZIO[Scope, Throwable, Unit] = for {
    graphs <- projectService.getOntologyGraphsForProject(project)
    _      <- ZIO
           .fail(new RuntimeException(s"Project '${project.id}' has no ontologies in the triplestore."))
           .when(graphs.isEmpty)
    _             <- ZIO.logInfo(s"$taskId: Fetching ontologies '${graphs.map(_.value).mkString(",")}' for validation")
    tempDir       <- storage.tempDataImportScoped(taskId)
    ontologyFiles <- ZIO.foreachPar(Chunk.fromIterable(graphs).zipWithIndex) { (g, i) =>
                       val file = tempDir / s"ontology-${i + 1}.nq"
                       Files.createFile(file) *> triplestore.downloadGraph(g, file, NQuads).as(file)
                     }
    ontologyFilesNec <- ZIO
                          .fromOption(NonEmptyChunk.fromChunk(ontologyFiles))
                          .orDieWith(_ => new IllegalStateException("ontologyFiles is empty despite non-empty graphs"))
    adminFile = tempDir / "admin.nq"
    _        <- triplestore.queryToFile(AdminUsersQuery.build, adminDataNamedGraph, adminFile, NQuads)
    _        <- validator.validate(ontologyFilesNec, NonEmptyChunk(adminFile, dataFile), project.id)
  } yield ()

  // No ontology cache refresh: a data-graph import adds no ontology triples.
  private def uploadToTriplestore(nqFile: Path): Task[Unit] =
    triplestore.uploadNQuads(ZStream.fromFile(nqFile.toFile))

  def getImportStatus(importId: DataTaskId): IO[Option[Nothing], CurrentDataTask] = state.find(importId)

  def deleteImport(importId: DataTaskId): IO[Option[ImportInProgressError], Unit] =
    for {
      _ <- state
             .deleteIfNotInProgress(importId)
             .mapError {
               case Some(StateInProgressError(s)) => Some(ImportInProgressError(s))
               case None                          => None
             }
      dir <- storage.dataImportDir(importId).tap(dir => Files.deleteRecursive(dir).logError.ignore)
      _   <- ZIO.logInfo(s"$importId: Cleaned data import dir $dir")
    } yield ()
}

object ProjectDataImportService {
  val layer: URLayer[
    KnoraProjectService & PermissionsResponder & OntologyTransformer & ProjectMigrationImportValidator &
      TriplestoreService,
    ProjectDataImportService,
  ] = (ProjectDataImportStorageService.layer >+> FilesystemDataTaskPersistence.dataImportLayer) >>>
    ZLayer.derive[ProjectDataImportService]
}
