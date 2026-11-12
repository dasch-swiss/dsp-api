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
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.slice.`export`.domain.ProjectMigrationImportValidator.ImportMode
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.ontology.ConversionContext
import org.knora.webapi.slice.ontology.OntologyTransformer
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService

/**
 * Imports a project's data graph from a knora-api JSON-LD payload: transforms it to knora-base NQuads (assigned to
 * the project's data named graph), SHACL-validates the result against the project's ontologies fetched from the
 * triplestore, and streams it into the triplestore. Create-only: the project's data graph must contain no data other
 * than list nodes.
 */
final class ProjectDataImportService(
  state: DataTaskState,
  storage: ProjectDataImportStorageService,
  projectService: KnoraProjectService,
  ontologyRepo: OntologyRepo,
  transformer: OntologyTransformer,
  validator: ProjectMigrationImportValidator,
  triplestore: TriplestoreService,
  slot: ImportSlot,
) { self =>

  /** Whether the project's data named graph already contains data other than list nodes (create-only precondition, R7). */
  def dataGraphExists(project: KnoraProject): Task[Boolean] =
    triplestore.query(ProjectDataGraphExistsQuery.build(project))

  /**
   * `createdBy` and `onBehalfOf` are both `User` and carry opposite meanings (actor vs subject) — call with named
   * arguments to prevent a silent positional swap that would invert audit and ownership.
   *
   * @param createdBy  the triggering system admin, recorded as the task owner for audit.
   * @param onBehalfOf the project user every imported resource and value is attributed to, and whose default object
   *                   access permissions are resolved. Its eligibility is captured once at trigger time (no
   *                   mid-flight re-check).
   * @param limit      the payload size limit in bytes; only tests lower it.
   */
  def importDataGraph(
    project: KnoraProject,
    createdBy: User,
    onBehalfOf: User,
    stream: ZStream[Any, Throwable, Byte],
    limit: Long = ProjectDataImportService.maxPayloadBytes,
  ): IO[ImportExistsError | ImportSlotBusyError, CurrentDataTask] =
    slot
      .run(
        state
          .startWith(state.makeNew(project.id, createdBy, Some(onBehalfOf.userIri)))(
            saveJsonLdStream(_, stream, limit),
          )
          .mapError { case StatesExistError(t) => ImportExistsError(t) },
      ) { case (task, withinLimit) =>
        doImport(task.id, project, onBehalfOf)
          .whenZIO(state.isInProgress(task.id))
          .when(withinLimit)
          .unit
          .onError(state.failWith(task.id, _))
      }
      .map(_._1)

  /** Saves the payload; if it exceeds the limit, fails the task and returns `false`. */
  private def saveJsonLdStream(taskId: DataTaskId, stream: ZStream[Any, Throwable, Byte], limit: Long): Task[Boolean] =
    for {
      _           <- storage.dataImportDir(taskId)
      jsonLdPath  <- storage.dataImportJsonLdPath(taskId)
      withinLimit <- ProjectDataImportService
                       .saveWithinLimit(stream, jsonLdPath, limit)
                       .onError(_ => Files.deleteIfExists(jsonLdPath).ignore)
      _ <- state
             .fail(taskId, ProjectDataImportService.payloadTooLargeMessage(limit))
             .ignore
             .unless(withinLimit)
    } yield withinLimit

  // The `onBehalfOf` user is captured once at trigger time; per-entity permissions are resolved later inside the
  // transformer from this snapshot. `doImport` does not re-fetch or re-validate the user, so this service needs no
  // `UserService`. A user deleted mid-flight still fails the SHACL validation step before the write.
  private def doImport(taskId: DataTaskId, project: KnoraProject, onBehalfOf: User): UIO[Unit] =
    ZIO.scoped {
      for {
        _          <- ZIO.logInfo(s"$taskId: Starting data import for project '${project.id}'")
        jsonLdPath <- storage.dataImportJsonLdPath(taskId)

        ctx          = ConversionContext(onBehalfOf, project)
        transformed <- transformer
                         .toKnoraBase(jsonLdPath.toFile.toPath, ctx)
                         .mapError(e => new RuntimeException(s"Transformation failed: ${e.message}"))
        _ <- ZIO.logInfo(s"$taskId: Transformed data to knora-base for project '${project.id}'")

        _ <- validate(taskId, project, Path.fromJava(transformed), onBehalfOf)
        _ <- ZIO.logInfo(s"$taskId: Validation passed for project '${project.id}'")

        // Re-verify the create-only precondition immediately before the upload: the slot blocks concurrent imports
        // on this instance, so this narrows the race against writes outside the slot (other API paths, other
        // instances) but does not close it — acceptable for an admin-only operation.
        // Wording kept aligned with `V3ErrorCode.data_graph_exists` ("exists" now means "holds non-list data").
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
   * graphs are fetched from the triplestore — the payload carries instance data only — and the project ontology
   * shapes are generated from the built-in and project ontologies. Shared ontologies are not loaded. The data shapes also check
   * `attachedToUser` references against `knora-admin:User` instances, so a projection of the user typing triples
   * is included in the data files (only the typing triples — downloading the full admin graph would scale with
   * instance size, not import size). Including it in the data chunk means the shared validator's placeholder scan
   * also runs over it, which is harmless for typing-only triples. The project's list nodes are fetched with a
   * CONSTRUCT projection, independent of whether the project data graph exists.
   */
  private def validate(
    taskId: DataTaskId,
    project: KnoraProject,
    dataFile: Path,
    onBehalfOf: User,
  ): ZIO[Scope, Throwable, Unit] = for {
    projectOntologies <- ontologyRepo.findByProject(project)
    graphs             = projectOntologies.map(_.ontologyMetadata.ontologyIri.toInternalIri)
    // Backstop for the synchronous `project_ontologies_missing` check in the RestService: defends against a project
    // whose ontologies vanish between trigger and validation. Message aligned with `V3ErrorCode.project_ontologies_missing`.
    _ <- ZIO
           .fail(new RuntimeException(s"The project '${project.id}' has no ontologies in the triplestore."))
           .when(graphs.isEmpty)
    _                <- ZIO.logInfo(s"$taskId: Fetching ontologies '${graphs.map(_.value).mkString(",")}' for validation")
    tempDir          <- storage.tempDataImportScoped(taskId)
    ontologyFiles    <- downloadOntologies(graphs, tempDir)
    ontologyFilesNec <- ZIO
                          .fromOption(NonEmptyChunk.fromChunk(ontologyFiles))
                          .orDieWith(_ => new IllegalStateException("ontologyFiles is empty despite non-empty graphs"))
    adminFile = tempDir / "admin.nq"
    _        <- triplestore.queryToFile(AdminUsersQuery.build, adminDataNamedGraph, adminFile, NQuads)
    // At import time the project's data graph holds only list nodes (create-only).
    listNodesFile = tempDir / "list-nodes.nq"
    dataGraph     = projectService.getDataGraphForProject(project)
    _            <- triplestore.queryToFile(ProjectListNodesQuery.build(dataGraph), dataGraph, listNodesFile, NQuads)
    // The validator requires exactly one named graph per file, so an empty result (no lists) is not passed on.
    listNodes <- Files.size(listNodesFile).map(size => if size > 0 then Chunk(listNodesFile) else Chunk.empty)
    shapes    <- loadOntologyShapes(projectOntologies)
    _         <- validator.validate(
           ontologyFilesNec,
           (NonEmptyChunk(adminFile) ++ listNodes) :+ dataFile,
           project.id,
           ImportMode.BulkData(onBehalfOf.userIri, shapes),
         )
  } yield ()

  private def downloadOntologies(graphs: List[InternalIri], tempDir: Path): Task[Chunk[Path]] =
    ZIO.foreachPar(Chunk.fromIterable(graphs).zipWithIndex) { (g, i) =>
      val file = tempDir / s"ontology-${i + 1}.nq"
      Files.createFile(file) *> triplestore.downloadGraph(g, file, NQuads).as(file)
    }

  /** The SHACL shapes of the project's resource classes, generated from the built-in and project ontologies. */
  private def loadOntologyShapes(projectOntologies: List[ReadOntologyV2]): Task[String] =
    ontologyRepo.findAll().mapAttempt { all =>
      val builtIn = all.filter(_.ontologyMetadata.ontologyIri.isKnoraBuiltInDefinitionIri).toList
      ProjectOntologyShapes.generate(builtIn ++ projectOntologies)
    }

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

  private val BytesPerMiB = 1024L * 1024L

  /**
   * The maximum accepted size of the JSON-LD payload: 250 MiB.
   *
   * The JSON-LD parser holds the whole document in the heap, at 10-16 times the payload size, and an out-of-memory
   * error stops the whole API. Do not raise it before the JSON-LD parsing streams.
   */
  val maxPayloadBytes: Long = 250L * BytesPerMiB

  /** The limit in human-readable form, e.g. "250 MiB". */
  def humanLimit(limit: Long): String = s"${limit / BytesPerMiB} MiB"

  def payloadTooLargeMessage(limit: Long): String = s"The payload exceeds the limit of ${humanLimit(limit)}."

  /** A declared `Content-Length` above the limit. */
  final case class PayloadTooLargeError(declared: Long, limit: Long)

  /** A missing declared length passes; a length equal to the limit passes. */
  def checkDeclaredLength(declared: Option[Long], limit: Long): Either[PayloadTooLargeError, Unit] =
    declared match {
      case Some(d) if d > limit => Left(PayloadTooLargeError(d, limit))
      case _                    => Right(())
    }

  /**
   * Writes at most `limit + 1` bytes of the stream to the file. Returns `true` if the stream fits within the limit.
   * Otherwise deletes the partial file and returns `false`.
   */
  def saveWithinLimit(stream: ZStream[Any, Throwable, Byte], path: Path, limit: Long): Task[Boolean] =
    for {
      written    <- stream.take(limit + 1).run(ZSink.fromFile(path.toFile))
      withinLimit = written <= limit
      _          <- Files.deleteIfExists(path).unless(withinLimit)
    } yield withinLimit

  val layer: URLayer[
    ImportSlot & KnoraProjectService & OntologyRepo & OntologyTransformer & ProjectMigrationImportValidator &
      TriplestoreService,
    ProjectDataImportService,
  ] = (ProjectDataImportStorageService.layer >+> FilesystemDataTaskPersistence.dataImportLayer) >>>
    ZLayer.derive[ProjectDataImportService]
}
