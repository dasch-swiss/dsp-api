/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.vocabulary.RDF
import zio.*
import zio.nio.file.Files
import zio.nio.file.Path
import zio.stream.ZSink
import zio.stream.ZStream

import java.io.IOException
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

import org.knora.bagit.BagIt
import org.knora.bagit.BagItError
import org.knora.bagit.domain.Bag
import org.knora.webapi.KnoraBaseVersion
import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.common.jena.DatasetOps
import org.knora.webapi.slice.common.jena.JenaConversions.given
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService

// This error is used to indicate that an import already exists.
final case class ImportExistsError(t: CurrentDataTask)
// This error is used to indicate that an import is already in progress.
final case class ImportInProgressError(t: CurrentDataTask)

final class ProjectMigrationImportService(
  state: DataTaskState,
  groupService: KnoraGroupService,
  projectService: KnoraProjectService,
  storage: ProjectMigrationStorageService,
  triplestore: TriplestoreService,
  ontologyCache: OntologyCache,
  userService: KnoraUserService,
) { self =>

  def importDataExport(
    projectIri: ProjectIri,
    createdBy: User,
    stream: ZStream[Any, Throwable, Byte],
  ): IO[ImportExistsError, CurrentDataTask] = for {
    importTask <- state.makeNew(projectIri, createdBy).mapError { case StatesExistError(t) => ImportExistsError(t) }
    _          <- saveZipStream(importTask.id, stream)
    _          <- doImport(importTask.id, projectIri).whenZIO(state.isInProgress(importTask.id)).forkDaemon
  } yield importTask

  private def saveZipStream(taskId: DataTaskId, stream: ZStream[Any, Throwable, Byte]) = (
    for {
      _         <- storage.importDir(taskId)
      bagItPath <- storage.importBagItZipPath(taskId)
      _         <- stream.run(ZSink.fromFile(bagItPath.toFile))
    } yield ()
  ).tapError(_ => state.fail(taskId).ignore).orDie

  private def doImport(taskId: DataTaskId, projectIri: ProjectIri): UIO[Unit] =
    ZIO.scoped {
      for {
        tempZip           <- storage.tempImportScoped(taskId)
        (tempDir, zipFile) = tempZip
        _                 <- ZIO.logInfo(s"$taskId: Starting import for project '$projectIri'")

        result <- BagIt.readAndValidateZip(zipFile, Some(tempDir)).mapError {
                    case ex: IOException => ex: Throwable
                    case err: BagItError => new RuntimeException(err.message)
                  }
        (bag, bagRoot) = result
        _             <- ZIO.logInfo(s"$taskId: BagIt validation passed for project '$projectIri'")

        _ <- validateVersionCompatibility(bag, taskId)
        _ <- ZIO.logInfo(s"$taskId: Version compatibility validated for project '$projectIri'")

        nqFiles <- validateRdfPayloadFiles(bagRoot)
        _       <- ZIO.logInfo(
               s"$taskId: Payload file validation passed for project '$projectIri', found: ${nqFiles.mkString(", ")}",
             )

        _ <- ZIO.scoped { // rdf validation, close scope for model right after validation to free up memory
               for {
                 model <- loadModel(bagRoot, "admin.nq")
                 _     <- validateProjectNotExists(bag, projectIri, model)
                 _     <- ZIO.logInfo(s"$taskId: Project conflict checks passed for project '$projectIri'")
                 _     <- validateUsersNotExist(model)
                 _     <- ZIO.logInfo(s"$taskId: User conflict checks passed for project '$projectIri'")
                 _     <- validateGroupsNotExist(model)
                 _     <- ZIO.logInfo(s"$taskId: Group conflict checks passed for project '$projectIri'")
               } yield ()
             }

        // upload data
        _ <- uploadRdfDataToTriplestore(nqFiles)
        _ <- ZIO.logInfo(s"$taskId: RDF data uploaded to triplestore for project '$projectIri'")
        _ <- state.complete(taskId).ignore
        _ <- ZIO.logInfo(s"$taskId: Import completed for project '$projectIri'")
      } yield ()
    }.logError.catchAll(e =>
      ZIO.logError(s"$taskId: Import failed for project '$projectIri' with error: ${e.getMessage}") *>
        state.fail(taskId).ignore,
    )

  private def loadModel(bagRoot: Path, fileName: String): ZIO[Scope, Throwable, Model] =
    DatasetOps.from(bagRoot / "data" / "rdf" / fileName, Lang.NQUADS).map(_.getUnionModel)

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

  private def validateProjectNotExists(
    bag: Bag,
    projectIri: ProjectIri,
    model: Model,
  ): Task[Unit] = for {
    // Parse External-Identifier from bag-info.txt
    externalId <- ZIO
                    .fromOption(bag.bagInfo.flatMap(_.externalIdentifier))
                    .orElseFail(
                      new RuntimeException("Required field 'External-Identifier' is missing from bag-info.txt"),
                    )
    // Validate it's a valid ProjectIri
    bagProjectIri <-
      ZIO
        .fromEither(ProjectIri.from(externalId))
        .mapError(e => new RuntimeException(s"External-Identifier '$externalId' is not a valid project IRI: $e"))
    // Validate it matches the endpoint's projectIri
    _ <-
      ZIO.when(bagProjectIri != projectIri)(
        ZIO.fail(
          new RuntimeException(
            s"External-Identifier '${bagProjectIri.value}' does not match the endpoint project IRI '${projectIri.value}'",
          ),
        ),
      )
    // Check project doesn't already exist by IRI
    exists <- projectService.existsById(projectIri)
    _      <- ZIO.when(exists)(
           ZIO.fail(new RuntimeException(s"Project with IRI '${projectIri.value}' already exists")),
         )
    // Validate project presence and shortcode in admin data
    projectResource <- ZIO
                         .fromEither(model.resource(projectIri.value))
                         .mapError(e => new RuntimeException(s"$e in admin.nq"))
    _ <- ZIO.when(!projectResource.listProperties().hasNext)(
           ZIO.fail(
             new RuntimeException(
               s"Project IRI '${projectIri.value}' not found as a subject in admin.nq",
             ),
           ),
         )
    shortcode <- ZIO
                   .fromEither(projectResource.objectString(KnoraAdmin.ProjectShortcode, Shortcode.from))
                   .mapError(e => new RuntimeException(s"$e in admin.nq"))
    // Check shortcode doesn't already exist
    existsByShortcode <- projectService.findByShortcode(shortcode)
    _                 <- ZIO.when(existsByShortcode.isDefined)(
           ZIO.fail(
             new RuntimeException(s"Project with shortcode '${shortcode.value}' already exists"),
           ),
         )
  } yield ()

  private def validateUsersNotExist(model: Model): Task[Unit] = {
    val userResources = model.listSubjectsWithProperty(RDF.`type`, KnoraAdmin.User: Resource).asScala.toList
    ZIO.foreachDiscard(userResources) { userResource =>
      val userIriStr = userResource.getURI
      for {
        userIri <- ZIO
                     .fromEither(UserIri.from(userIriStr))
                     .mapError(e => new RuntimeException(s"Invalid user IRI '$userIriStr' in admin.nq: $e"))
        existsById <- userService.findById(userIri)
        _          <- ZIO.when(existsById.isDefined)(
               ZIO.fail(new RuntimeException(s"User with IRI '${userIri.value}' already exists")),
             )
        email <- ZIO
                   .fromEither(userResource.objectString(KnoraAdmin.Email, Email.from))
                   .mapError(e => new RuntimeException(s"$e in admin.nq"))
        existsByEmail <- userService.findByEmail(email)
        _             <- ZIO.when(existsByEmail.isDefined)(
               ZIO.fail(new RuntimeException(s"User with email '${email.value}' already exists")),
             )
        username <- ZIO
                      .fromEither(userResource.objectString(KnoraAdmin.Username, Username.from))
                      .mapError(e => new RuntimeException(s"$e in admin.nq"))
        existsByUsername <- userService.findByUsername(username)
        _                <- ZIO.when(existsByUsername.isDefined)(
               ZIO.fail(new RuntimeException(s"User with username '${username.value}' already exists")),
             )
      } yield ()
    }
  }

  private def validateGroupsNotExist(model: Model): Task[Unit] = {
    val groupResources = model.listSubjectsWithProperty(RDF.`type`, KnoraAdmin.UserGroup: Resource).asScala.toList
    ZIO.foreachDiscard(groupResources) { groupResource =>
      val groupIriStr = groupResource.getURI
      for {
        groupIri <- ZIO
                      .fromEither(GroupIri.from(groupIriStr))
                      .mapError(e => new RuntimeException(s"Invalid group IRI '$groupIriStr' in admin.nq: $e"))
        existsById <- groupService.findById(groupIri)
        _          <- ZIO.when(existsById.isDefined)(
               ZIO.fail(new RuntimeException(s"Group with IRI '${groupIri.value}' already exists")),
             )
        groupName <- ZIO
                       .fromEither(groupResource.objectString(KnoraAdmin.GroupName, GroupName.from))
                       .mapError(e => new RuntimeException(s"$e in admin.nq"))
        existsByName <- groupService.findByName(groupName)
        _            <- ZIO.when(existsByName.isDefined)(
               ZIO.fail(new RuntimeException(s"Group with name '${groupName.value}' already exists")),
             )
      } yield ()
    }
  }

  private def validateRdfPayloadFiles(bagRoot: Path): Task[Chunk[Path]] = {
    val rdfDir  = bagRoot / "data" / "rdf"
    val adminNq = rdfDir / "admin.nq"
    val dataNq  = rdfDir / "data.nq"
    for {
      // Check admin.nq exists
      _ <- ZIO.unlessZIO(Files.exists(adminNq))(
             ZIO.fail(new RuntimeException("Required file 'data/rdf/admin.nq' is missing from the BagIt payload")),
           )
      // Check data.nq exists
      _ <- ZIO.unlessZIO(Files.exists(dataNq))(
             ZIO.fail(new RuntimeException("Required file 'data/rdf/data.nq' is missing from the BagIt payload")),
           )
      // Find ontology files
      ontologyFiles <- Files.list(rdfDir).filter(_.filename.toString.matches("ontology-\\d+\\.nq")).runCollect
      _             <- ZIO.when(ontologyFiles.isEmpty)(
             ZIO.fail(
               new RuntimeException("No 'data/rdf/ontology-*.nq' files found in the BagIt payload"),
             ),
           )
      // Permissions are optional, if present, we want to include them
      permissionNq = rdfDir / "permission.nq"
      perms       <- ZIO.ifZIO(Files.exists(permissionNq))(ZIO.some(permissionNq), ZIO.none)
      rdfFiles     = ontologyFiles ++ Chunk(adminNq, dataNq) ++ Chunk.fromIterable(perms)
      // Validate we can load files as RDF, this is a sanity check to avoid starting an import that
      // will fail later due to malformed RDF.
      // Exclude admin.nq from this check to avoid loading the whole model twice, it is checked separately.
      _ <- ZIO.foreachDiscard(rdfFiles.filter(_ != adminNq))(file => ZIO.scoped(DatasetOps.from(file, Lang.NQUADS)))
    } yield rdfFiles
  }

  private def uploadRdfDataToTriplestore(nqFiles: Chunk[Path]): Task[Unit] = {
    val newline = ZStream.fromChunk(Chunk('\n'.toByte))
    // When concatenating files, if a file doesn't end with a newline,
    // the last line of one file merges with the first line of the next file, corrupting both NQuad statements.
    // To prevent this, we insert a newline between files.
    val stream = ZStream.fromIterable(nqFiles).flatMap(p => ZStream.fromFile(p.toFile) ++ newline)
    triplestore.uploadNQuads(stream) *> ontologyCache.refreshCache().unit
  }

  private def getUniqueField(fields: List[(String, String)], name: String): Task[String] =
    fields.filter(_._1 == name) match {
      case Nil          => ZIO.fail(new RuntimeException(s"Required field '$name' is missing from bag-info.txt"))
      case List((_, v)) => ZIO.succeed(v)
      case _            => ZIO.fail(new RuntimeException(s"Field '$name' appears more than once in bag-info.txt"))
    }

  def getImportStatus(importId: DataTaskId): IO[Option[Nothing], CurrentDataTask] = state.find(importId)

  def deleteImport(importId: DataTaskId): IO[Option[ImportInProgressError], Unit] =
    for {
      _ <- state
             .deleteIfNotInProgress(importId)
             .mapError {
               case Some(StateInProgressError(s)) => Some(ImportInProgressError(s))
               case None                          => None
             }
      dir <- storage.importDir(importId).tap(dir => Files.deleteRecursive(dir).logError.orDie)
      _   <- ZIO.logInfo(s"$importId: Cleaned import dir $dir")
    } yield ()
}

object ProjectMigrationImportService {
  val layer = FilesystemDataTaskPersistence.importLayer >>> ZLayer.derive[ProjectMigrationImportService]
}
