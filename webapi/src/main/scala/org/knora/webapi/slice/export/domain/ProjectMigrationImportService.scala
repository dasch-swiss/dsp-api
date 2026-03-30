/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory
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
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.DspIngestClient
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
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
  dspIngestClient: DspIngestClient,
  groupService: KnoraGroupService,
  projectService: KnoraProjectService,
  projectShaclValidator: ProjectMigrationImportShaclValidator,
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

        payloadFiles              <- validateRdfPayloadFiles(bagRoot)
        (ontologyFiles, dataFiles) = payloadFiles
        nqFiles                    = ontologyFiles ++ dataFiles
        _                         <- ZIO.logInfo(
               s"$taskId: Payload file validation passed for project '$projectIri', found: ${nqFiles.mkString(", ")}",
             )

        _          <- ZIO.logInfo(s"$taskId: Starting admin data validation for project '$projectIri'")
        adminNqPath = bagRoot / "data" / "rdf" / "admin.nq"
        // First parse of admin.nq: validate project/group uniqueness before SHACL.
        // This is intentionally separate from the second parse below because:
        // 1. SHACL must validate the *original* admin.nq with all user type declarations present.
        // 2. The second parse (prepareAdminModel) rewrites user triples, so it must run *after* SHACL.
        // 3. Keeping them in separate scoped blocks lets Jena release the first dataset's memory.
        shortcode <- ZIO.scoped {
                       for {
                         dataset   <- DatasetOps.from(adminNqPath, Lang.NQUADS)
                         model      = dataset.getNamedModel(adminDataNamedGraph.value)
                         shortcode <- validateProjectNotExists(bag, projectIri, model)
                         _         <- ZIO.logInfo(s"$taskId: Project conflict checks passed for project '$projectIri'")
                         _         <- validateGroupsNotExist(model)
                         _         <- ZIO.logInfo(s"$taskId: Group conflict checks passed for project '$projectIri'")
                       } yield shortcode
                     }
        _ <- ZIO.logInfo(s"$taskId: Admin data validation passed for project '$projectIri'")

        // SHACL validates the original admin.nq (all user type declarations present)
        _ <- ZIO.logInfo(s"$taskId: Starting shacl validation '$projectIri'")
        _ <- projectShaclValidator.validate(ontologyFiles, dataFiles, projectIri)
        _ <- ZIO.logInfo(s"$taskId: SHACL validation passed for project '$projectIri'")

        // Second parse of admin.nq: idempotent user handling rewrites user triples.
        _               <- ZIO.logInfo(s"$taskId: Starting user preparation for project '$projectIri'")
        rewrittenAdminNq = bagRoot / "data" / "rdf" / "admin-rewritten.nq"
        _               <- ZIO.scoped {
               for {
                 dataset <- DatasetOps.from(adminNqPath, Lang.NQUADS)
                 model    = dataset.getNamedModel(adminDataNamedGraph.value)
                 _       <- prepareAdminModel(model, projectIri)
                 _       <- ZIO.attempt {
                        val outDataset = org.apache.jena.query.DatasetFactory.create()
                        try {
                          outDataset.addNamedModel(adminDataNamedGraph.value, model)
                          val out = java.io.FileOutputStream(rewrittenAdminNq.toFile)
                          try org.apache.jena.riot.RDFDataMgr.write(out, outDataset, org.apache.jena.riot.Lang.NQUADS)
                          finally out.close()
                        } finally outDataset.close()
                      }
               } yield ()
             }
        _ <- ZIO.logInfo(s"$taskId: User preparation completed for project '$projectIri'")

        // Replace admin.nq with rewritten version in the NQuad upload
        rewrittenDataFiles = dataFiles.map(p => if (p == adminNqPath) rewrittenAdminNq else p)
        rewrittenNqFiles   = ontologyFiles ++ rewrittenDataFiles

        // import assets from ingest if present
        assetZipPath = bagRoot / "data" / "assets" / "assets.zip"
        _           <- ZIO.ifZIO(Files.exists(assetZipPath))(
               ZIO.logInfo(s"$taskId: Importing assets for project '$projectIri'") *>
                 dspIngestClient.importProject(shortcode, assetZipPath) *>
                 ZIO.logInfo(s"$taskId: Assets imported for project '$projectIri'"),
               ZIO.logInfo(s"$taskId: No assets/assets.zip found, skipping asset import for project '$projectIri'"),
             )

        // upload data (using rewritten admin.nq)
        _ <- uploadRdfDataToTriplestore(rewrittenNqFiles)
        _ <- ZIO.logInfo(s"$taskId: RDF data uploaded to triplestore for project '$projectIri'")
        _ <- state.complete(taskId).ignore
        _ <- ZIO.logInfo(s"$taskId: Import completed for project '$projectIri'")
      } yield ()
    }.logError.catchAll(e =>
      ZIO.logError(s"$taskId: Import failed for project '$projectIri' with error: ${e.getMessage}") *>
        state.fail(taskId).ignore,
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

  private def validateProjectNotExists(
    bag: Bag,
    projectIri: ProjectIri,
    model: Model,
  ): Task[Shortcode] = for {
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
            s"External-Identifier '$bagProjectIri' does not match the endpoint project IRI '$projectIri'",
          ),
        ),
      )
    // Check project doesn't already exist by IRI
    exists <- projectService.existsById(projectIri)
    _      <- ZIO.when(exists)(
           ZIO.fail(new RuntimeException(s"Project with IRI '$projectIri' already exists")),
         )
    // Validate project presence and shortcode in admin data
    projectResource <- ZIO
                         .fromEither(model.resource(projectIri.value))
                         .mapError(e => new RuntimeException(s"$e in admin.nq"))
    _ <- ZIO.when(!projectResource.listProperties().hasNext)(
           ZIO.fail(new RuntimeException(s"Project IRI '$projectIri' not found as a subject in admin.nq")),
         )
    shortcode <- ZIO
                   .fromEither(projectResource.objectString(KnoraAdmin.ProjectShortcode, Shortcode.from))
                   .mapError(e => new RuntimeException(s"$e in admin.nq"))
    // Check shortcode doesn't already exist
    existsByShortcode <- projectService.findByShortcode(shortcode)
    _                 <- ZIO.when(existsByShortcode.isDefined)(
           ZIO.fail(
             new RuntimeException(s"Project with shortcode '$shortcode' already exists"),
           ),
         )
  } yield shortcode

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
               ZIO.fail(new RuntimeException(s"Group with IRI '$groupIri' already exists")),
             )
      } yield ()
    }
  }

  /**
   * Validates users and rewrites the admin.nq Jena model in-place before the single bulk upload.
   *
   * 1. Strips built-in user triples (SystemUser, AnonymousUser) — they already exist on every instance.
   * 2. For each remaining user, looks up by IRI, email, and username:
   *    - Found by IRI: verify identity match, log profile diffs, rewrite to scoped memberships only.
   *    - Not found at all: fail on root user, strip SystemAdmin, scope memberships.
   *    - No IRI match but email/username collision: fail with details.
   */
  private def prepareAdminModel(model: Model, projectIri: ProjectIri): Task[Unit] = for {
    // Step 1: Strip built-in user triples
    _ <- ZIO.attempt {
           val builtInIris = KnoraUserRepo.builtIn.all.map(_.id.value)
           builtInIris.foreach { iri =>
             val resource = model.getResource(iri)
             if (resource.listProperties().hasNext) {
               val stmts = resource.listProperties().asScala.toList
               stmts.foreach(model.remove)
               model.listStatements(null, null, resource).asScala.toList.foreach(model.remove)
             }
           }
         }

    // Step 2: Process remaining users
    userResources = model.listSubjectsWithProperty(RDF.`type`, KnoraAdmin.User: Resource).asScala.toList
    _            <- ZIO.foreachDiscard(userResources) { userResource =>
           val userIriStr = userResource.getURI
           for {
             userIri <- ZIO
                          .fromEither(UserIri.from(userIriStr))
                          .mapError(e => new RuntimeException(s"Invalid user IRI '$userIriStr': $e"))
             email <- ZIO
                        .fromEither(userResource.objectString(KnoraAdmin.Email, Email.from))
                        .mapError(e => new RuntimeException(s"$e in admin.nq"))
             username <- ZIO
                           .fromEither(userResource.objectString(KnoraAdmin.Username, Username.from))
                           .mapError(e => new RuntimeException(s"$e in admin.nq"))
             byIri      <- userService.findById(userIri)
             byEmail    <- userService.findByEmail(email)
             byUsername <- userService.findByUsername(username)
             _          <- (byIri, byEmail, byUsername) match {
                    case (Some(existingUser), _, _) =>
                      // Found by IRI — reject root, verify identity match, log diffs, rewrite to scoped memberships
                      ZIO.when(username.value == "root")(
                        ZIO.fail(
                          new RuntimeException(
                            s"Import contains the root user '${userIri.value}'. Resources referencing root require pre-migration cleanup.",
                          ),
                        ),
                      ) *>
                        validateIdentityMatch(existingUser, email, username, userIri) *>
                        logProfileDifferences(existingUser, userResource, userIri) *>
                        ZIO.attempt(rewriteExistingUserTriples(model, userResource, projectIri))
                    case (None, None, None) =>
                      // Entirely new user
                      ZIO.attempt(rewriteNewUserTriples(model, userResource, projectIri, username))
                    case (None, Some(emailOwner), _) =>
                      ZIO.fail(
                        new RuntimeException(
                          s"User '${userIri.value}' has email '${email.value}' which is already used by user '${emailOwner.id.value}'",
                        ),
                      )
                    case (None, _, Some(usernameOwner)) =>
                      ZIO.fail(
                        new RuntimeException(
                          s"User '${userIri.value}' has username '${username.value}' which is already used by user '${usernameOwner.id.value}'",
                        ),
                      )
                  }
           } yield ()
         }
  } yield ()

  private def validateIdentityMatch(
    existing: KnoraUser,
    email: Email,
    username: Username,
    userIri: UserIri,
  ): Task[Unit] =
    for {
      _ <- ZIO.when(existing.email != email)(
             ZIO.fail(
               new RuntimeException(
                 s"User '$userIri' has email '${email.value}' but existing user has email '${existing.email.value}'",
               ),
             ),
           )
      _ <-
        ZIO.when(existing.username != username)(
          ZIO.fail(
            new RuntimeException(
              s"User '$userIri' has username '${username.value}' but existing user has username '${existing.username.value}'",
            ),
          ),
        )
    } yield ()

  private def logProfileDifferences(existing: KnoraUser, userResource: Resource, userIri: UserIri): Task[Unit] = {
    val diffs    = List.newBuilder[String]
    val warnings = List.newBuilder[String]

    userResource.objectStringOption(KnoraAdmin.FamilyName) match {
      case Right(Some(v)) =>
        if (v != existing.familyName.value) diffs += s"familyName: import='$v' existing='${existing.familyName.value}'"
      case Left(err) => warnings += s"familyName: $err"
      case _         => ()
    }
    userResource.objectStringOption(KnoraAdmin.GivenName) match {
      case Right(Some(v)) =>
        if (v != existing.givenName.value) diffs += s"givenName: import='$v' existing='${existing.givenName.value}'"
      case Left(err) => warnings += s"givenName: $err"
      case _         => ()
    }

    val diffResult    = diffs.result()
    val warningResult = warnings.result()
    ZIO
      .when(warningResult.nonEmpty)(
        ZIO.logWarning(s"User '$userIri' has malformed profile fields in admin.nq: ${warningResult.mkString("; ")}"),
      )
      .unit *>
      ZIO
        .when(diffResult.nonEmpty)(
          ZIO.logWarning(
            s"User '$userIri' profile differs from existing: ${diffResult.mkString("; ")}. Keeping existing values.",
          ),
        )
        .unit
  }

  /**
   * For an existing user: remove all triples, add back only scoped membership triples.
   */
  private def rewriteExistingUserTriples(model: Model, userResource: Resource, projectIri: ProjectIri): Unit = {
    val projectRes = model.getResource(projectIri.value)
    // Collect scoped memberships before removing triples
    import AdminModelScoping.{isInProjectProp, isInGroupProp, isInProjectAdminGroupProp, belongsToProjectProp}

    val hasIsInProject = userResource.hasProperty(isInProjectProp, projectRes)
    val scopedGroups   = userResource
      .listProperties(isInGroupProp)
      .asScala
      .filter(stmt => stmt.getObject.asResource().hasProperty(belongsToProjectProp, projectRes))
      .map(_.getObject.asResource().getURI)
      .toList
    val hasProjectAdmin = userResource.hasProperty(isInProjectAdminGroupProp, projectRes)

    // Remove all user triples
    val stmts = userResource.listProperties().asScala.toList
    stmts.foreach(model.remove)

    // Add back only scoped membership triples
    if (hasIsInProject)
      val _ = model.add(userResource, isInProjectProp, projectRes)
    scopedGroups.foreach { groupIri =>
      val _ = model.add(userResource, isInGroupProp, model.getResource(groupIri))
    }
    if (hasProjectAdmin)
      val _ = model.add(userResource, isInProjectAdminGroupProp, projectRes)
  }

  /**
   * For a new user: fail on root, strip cross-project memberships and SystemAdmin.
   */
  private def rewriteNewUserTriples(
    model: Model,
    userResource: Resource,
    projectIri: ProjectIri,
    username: Username,
  ): Unit = {
    // Fail on root user
    if (username.value == "root")
      throw new RuntimeException(
        s"Import contains the root user '${userResource.getURI}'. Resources referencing root require pre-migration cleanup.",
      )

    // Strip isInSystemAdminGroup
    val sysAdminProp = AdminModelScoping.isInSystemAdminGroupProp
    val sysAdminStmt = userResource.getProperty(sysAdminProp)
    if (sysAdminStmt != null && sysAdminStmt.getObject.isLiteral && sysAdminStmt.getLiteral.getBoolean)
      model.remove(sysAdminStmt)
      val _ = model.add(userResource, sysAdminProp, ResourceFactory.createTypedLiteral(false))

    // Strip cross-project memberships (defense-in-depth)
    AdminModelScoping.removeNonProjectMemberships(model, projectIri.value)
  }

  private def validateRdfPayloadFiles(bagRoot: Path): Task[(NonEmptyChunk[Path], NonEmptyChunk[Path])] = {
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
      ontologyFiles    <- Files.list(rdfDir).filter(_.filename.toString.matches("ontology-\\d+\\.nq")).runCollect
      ontologyFilesNec <- ZIO
                            .fromOption(NonEmptyChunk.fromChunk(ontologyFiles))
                            .orElseFail(
                              new RuntimeException("No 'data/rdf/ontology-*.nq' files found in the BagIt payload"),
                            )
      // Permissions are optional, if present, we want to include them
      permissionNq = rdfDir / "permission.nq"
      perms       <- ZIO.ifZIO(Files.exists(permissionNq))(ZIO.some(permissionNq), ZIO.none)
      dataFiles    = NonEmptyChunk(adminNq, dataNq) ++ Chunk.fromIterable(perms)
    } yield (ontologyFilesNec, dataFiles)
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
      dir <- storage.importDir(importId).tap(dir => Files.deleteRecursive(dir).logError.ignore)
      _   <- ZIO.logInfo(s"$importId: Cleaned import dir $dir")
    } yield ()
}

object ProjectMigrationImportService {
  val layer: URLayer[
    DspIngestClient & KnoraGroupService & KnoraProjectService & ProjectMigrationImportShaclValidator &
      ProjectMigrationStorageService & TriplestoreService & OntologyCache & KnoraUserService,
    ProjectMigrationImportService,
  ] = FilesystemDataTaskPersistence.importLayer >>> ZLayer.derive[ProjectMigrationImportService]
}
