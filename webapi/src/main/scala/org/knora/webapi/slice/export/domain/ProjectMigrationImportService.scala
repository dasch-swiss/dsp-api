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
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.common.jena.DatasetOps
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
        (bag, bagRoot) = result
        _             <- ZIO.logInfo(s"$taskId: BagIt validation passed for project '$projectIri'")
        _             <- validateVersionCompatibility(bag, taskId)
        _             <- ZIO.logInfo(s"$taskId: Version compatibility validated for project '$projectIri'")
        _             <- validateProjectNotExists(bag, projectIri, bagRoot)
        _             <- ZIO.logInfo(s"$taskId: Project conflict checks passed for project '$projectIri'")
        _             <- validateUsersNotExist(bagRoot)
        _             <- ZIO.logInfo(s"$taskId: User conflict checks passed for project '$projectIri'")
        _             <- currentImport.complete(taskId).ignore
        _             <- ZIO.logInfo(s"$taskId: Import completed for project '$projectIri'")
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

  private def validateProjectNotExists(
    bag: Bag,
    projectIri: ProjectIri,
    bagRoot: zio.nio.file.Path,
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
    // Load admin.nq and validate project presence and shortcode
    adminNqPath = (bagRoot / "data" / "rdf" / "admin.nq").toFile.toPath
    _          <- ZIO.scoped {
           for {
             ds <- DatasetOps
                     .fromNQuadsFiles(List(adminNqPath))
                     .mapError(e => new RuntimeException(e))
             model           = ds.getUnionModel()
             projectResource = model.getResource(projectIri.value)
             // Verify project IRI appears as a subject in admin data
             _ <- ZIO.when(!projectResource.listProperties().hasNext)(
                    ZIO.fail(
                      new RuntimeException(
                        s"Project IRI '${projectIri.value}' not found as a subject in admin.nq",
                      ),
                    ),
                  )
             // Parse shortcode from admin data
             shortcodeProp  = model.createProperty(KnoraAdminPrefixExpansion, "projectShortcode")
             shortcodeStmt <- ZIO
                                .fromOption(Option(projectResource.getProperty(shortcodeProp)))
                                .orElseFail(
                                  new RuntimeException(
                                    s"Project '${projectIri.value}' has no projectShortcode in admin.nq",
                                  ),
                                )
             shortcodeStr = shortcodeStmt.getString
             shortcode   <- ZIO
                            .fromEither(Shortcode.from(shortcodeStr))
                            .mapError(e => new RuntimeException(s"Invalid shortcode '$shortcodeStr' in admin.nq: $e"))
             // Check shortcode doesn't already exist
             existsByShortcode <- projectService.findByShortcode(shortcode)
             _                 <- ZIO.when(existsByShortcode.isDefined)(
                    ZIO.fail(
                      new RuntimeException(s"Project with shortcode '${shortcode.value}' already exists"),
                    ),
                  )
           } yield ()
         }
  } yield ()

  private def validateUsersNotExist(bagRoot: zio.nio.file.Path): Task[Unit] = {
    val adminNqPath = (bagRoot / "data" / "rdf" / "admin.nq").toFile.toPath
    ZIO.scoped {
      for {
        ds           <- DatasetOps.fromNQuadsFiles(List(adminNqPath)).mapError(e => new RuntimeException(e))
        model         = ds.getUnionModel()
        rdfType       = model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "type")
        userType      = model.createResource(KnoraAdminPrefixExpansion + "User")
        emailProp     = model.createProperty(KnoraAdminPrefixExpansion, "email")
        usernameProp  = model.createProperty(KnoraAdminPrefixExpansion, "username")
        userResources = {
          val iter = model.listSubjectsWithProperty(rdfType, userType)
          val buf  = scala.collection.mutable.ListBuffer.empty[org.apache.jena.rdf.model.Resource]
          while (iter.hasNext) buf += iter.next()
          buf.toList
        }
        _ <- ZIO.foreachDiscard(userResources) { userResource =>
               val userIriStr = userResource.getURI
               for {
                 // Check by IRI
                 userIri <- ZIO
                              .fromEither(UserIri.from(userIriStr))
                              .mapError(e => new RuntimeException(s"Invalid user IRI '$userIriStr' in admin.nq: $e"))
                 existsById <- userService.findById(userIri)
                 _          <- ZIO.when(existsById.isDefined)(
                        ZIO.fail(
                          new RuntimeException(s"User with IRI '${userIri.value}' already exists"),
                        ),
                      )
                 // Check by email
                 emailStmt <- ZIO
                                .fromOption(Option(userResource.getProperty(emailProp)))
                                .orElseFail(
                                  new RuntimeException(s"User '${userIri.value}' has no email in admin.nq"),
                                )
                 emailStr = emailStmt.getString
                 email   <- ZIO
                            .fromEither(Email.from(emailStr))
                            .mapError(e => new RuntimeException(s"Invalid email '$emailStr' in admin.nq: $e"))
                 existsByEmail <- userService.findByEmail(email)
                 _             <- ZIO.when(existsByEmail.isDefined)(
                        ZIO.fail(
                          new RuntimeException(s"User with email '$emailStr' already exists"),
                        ),
                      )
                 // Check by username
                 usernameStmt <- ZIO
                                   .fromOption(Option(userResource.getProperty(usernameProp)))
                                   .orElseFail(
                                     new RuntimeException(
                                       s"User '${userIri.value}' has no username in admin.nq",
                                     ),
                                   )
                 usernameStr = usernameStmt.getString
                 username   <- ZIO
                               .fromEither(Username.from(usernameStr))
                               .mapError(e => new RuntimeException(s"Invalid username '$usernameStr' in admin.nq: $e"))
                 existsByUsername <- userService.findByUsername(username)
                 _                <- ZIO.when(existsByUsername.isDefined)(
                        ZIO.fail(
                          new RuntimeException(
                            s"User with username '${username.value}' already exists",
                          ),
                        ),
                      )
               } yield ()
             }
      } yield ()
    }
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
