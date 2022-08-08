/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.handler

import dsp.project.api.ProjectRepo
import dsp.project.domain.Project
import zio._

import java.util.UUID
import dsp.valueobjects.ProjectId
import dsp.valueobjects.Project._
import dsp.errors.NotFoundException
import dsp.valueobjects
import dsp.errors.DuplicateValueException

/**
 * The project handler.
 *
 * @param repo  the project repository
 */
final case class ProjectHandler(repo: ProjectRepo) {

  /**
   * Retrieves all projects (sorted by IRI).
   *
   * @return a list of all projects
   */
  def getProjects(): UIO[List[Project]] =
    repo.getProjects().map(_.sorted).tap(p => ZIO.logInfo(s"Got all projects: ${p.size}"))

  /**
   * Retrieves a project by ID.
   *
   * @param id  the project's ID
   * @return either a NotFoundException or the requested Project
   */
  def getProjectById(id: ProjectId): IO[NotFoundException, Project] =
    for {
      user <- repo
                .getProjectById(id)
                .mapError(_ => NotFoundException(s"Project with ID ${id} found"))
                .tapBoth(
                  _ => ZIO.logInfo(s"Could not find project with ID '${id}'"),
                  _ => ZIO.logInfo(s"Looked up project by ID '${id}'")
                )
    } yield user

  /**
   * Retrieves a project by short code.
   *
   * @param shortCode  the project's short code
   * @return either a NotFoundException or the requested Project
   */
  def getProjectByShortCode(shortCode: ShortCode): IO[NotFoundException, Project] =
    repo
      .getProjectByShortCode(shortCode.value)
      .mapError(_ => NotFoundException(s"Project with shortCode ${shortCode.value} not found"))
      .tapBoth(
        _ => ZIO.logInfo(s"Could not find project with shortCode '${shortCode}'"),
        _ => ZIO.logInfo(s"Looked up project by shortCode '${shortCode}'")
      )

  /**
   * Checks if a short code is already taken.
   *
   * @param shortCode  the project's shortCode
   * @return either a DuplicatedValueException or Unit if the short code is not taken
   */
  private def checkIfShortCodeTaken(shortCode: ShortCode): IO[DuplicateValueException, Unit] =
    // TODO-BL: wound't it be nicer to return the ID of the project here
    for {
      _ <- repo
             .checkShortCodeExists(shortCode.value)
             .mapError(_ => DuplicateValueException(s"ShortCode ${shortCode.value} already exists"))
             .tapBoth(
               _ => ZIO.logInfo(s"ShortCode '${shortCode}' is already taken"),
               _ => ZIO.logInfo(s"Checked shortCode '${shortCode}' which is not yet taken")
             )
    } yield ()

  /**
   * Creates a project given all necessary information.
   *
   * @param shortCode   the project's short code
   * @param name        the project's name
   * @param description the project descriptions
   * @return either a throwable if creation failed, or the ID of the newly created project
   */
  def createProject( // TODO-BL: why don't we simply pass the Project value object here?
    shortCode: ShortCode,
    name: String,       // TODO-BL: make ValueObject
    description: String // TODO-BL: make ValueObject
  ): IO[Throwable, ProjectId] =
    (for {
      _         <- checkIfShortCodeTaken(shortCode) // TODO: reserve shortcode
      id        <- ProjectId.make(shortCode).toZIO
      project   <- Project.make(id, name, description).toZIO
      projectID <- repo.storeProject(project)
    } yield projectID)
      .tapBoth(
        e => ZIO.logInfo(s"Failed to create project with shortCode '$shortCode': $e"),
        projectId => ZIO.logInfo(s"Created project with ID '${projectId}'")
      )

  /**
   * Deletes a project.
   *
   *  @param id  the project's ID
   *  @return either a NotFoundException or the project ID of the successfully deleted project
   */
  def deleteProject(id: ProjectId): IO[NotFoundException, ProjectId] =
    (for {
      _ <- repo
             .deleteProject(id)
             .mapError(_ => NotFoundException(s"Project with ID '${id}' not found"))
    } yield id)
      .tapBoth(
        _ => ZIO.logInfo(s"Could not delete project with ID '${id}'"),
        _ => ZIO.logInfo(s"Deleted project with ID '${id}'")
      )

  // TODO-BL: add update methods

//   def updateUsername(id: UserId, value: Username): IO[RequestRejectedException, UserId] =
//     for {
//       _ <- checkUsernameTaken(value)
//       // lock/reserve username
//       // check if user exists and get him, lock user
//       user        <- getUserById(id)
//       userUpdated <- ZIO.succeed(user.updateUsername(value))
//       _           <- repo.storeUser(userUpdated)
//     } yield id

//   def updatePassword(id: UserId, newPassword: Password, currentPassword: Password, requestingUser: User) = ???
//   // either the user himself or a sysadmin can change a user's password
//   // in both cases we need the current password of either the user itself or the sysadmin

//   def deleteUser(id: UserId): IO[NotFoundException, UserId] =
//     for {
//       _ <- repo
//              .deleteUser(id)
//              .mapError(_ => NotFoundException(s"User with ID ${id} not found"))
//     } yield id

}

/**
 * Companion object providing the layer with an initialized implementation
 */
object ProjectHandler {
  val layer: ZLayer[ProjectRepo, Nothing, ProjectHandler] =
    ZLayer {
      for {
        repo <- ZIO.service[ProjectRepo]
      } yield ProjectHandler(repo)
    }.tap(_ => ZIO.debug(">>> Project handler initialized <<<"))
}
