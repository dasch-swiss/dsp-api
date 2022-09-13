/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.handler

import zio._

import dsp.errors.DuplicateValueException
import dsp.errors.NotFoundException
import dsp.errors.RequestRejectedException
import dsp.project.api.ProjectRepo
import dsp.project.domain.Project
import dsp.valueobjects.Project._
import dsp.valueobjects._

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
      .getProjectByShortCode(shortCode)
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
    for {
      _ <- repo
             .checkIfShortCodeIsAvailable(shortCode)
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
   * @param description the project's descriptions
   * @return either a throwable if creation failed, or the ID of the newly created project
   */
  def createProject(
    project: Project
  ): IO[Throwable, ProjectId] =
    (for {
      _  <- checkIfShortCodeTaken(project.id.shortCode) // TODO: reserve shortcode
      id <- repo.storeProject(project)
    } yield id)
      .tapBoth(
        e => ZIO.logInfo(s"Failed to create project with shortCode '${project.id.shortCode}': $e"),
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

  /**
   * Updates the name of a project.
   *
   * @param id    the ID of the project to be updated
   * @param value the new name of the project
   * @return either the project ID if successfful, or a RequestRejectedException if not successful
   */
  def updateProjectName(id: ProjectId, value: Name): IO[RequestRejectedException, ProjectId] =
    (for {
      project        <- getProjectById(id)
      updatedProject <- project.updateProjectName(value).toZIO
      resultId       <- repo.storeProject(updatedProject)
    } yield resultId)
      .tapBoth(
        _ => ZIO.logInfo(s"Failed to update project name of project $id: "),
        _ => ZIO.logInfo(s"Successfully updated the name of project $id to '$value'")
      )

  /**
   * Updates the description of a project.
   *
   * @param id    the ID of the project to be updated
   * @param value the new description of the project
   * @return either the project ID if successful, or a RequestRejectedException if not successful
   */
  def updateProjectDescription(id: ProjectId, value: ProjectDescription): IO[RequestRejectedException, ProjectId] =
    (for {
      project        <- getProjectById(id)
      updatedProject <- project.updateProjectDescription(value).toZIO
      resultId       <- repo.storeProject(updatedProject)
    } yield resultId)
      .tapBoth(
        _ => ZIO.logInfo(s"Failed to update project description of project $id."),
        _ => ZIO.logInfo(s"Successfully updated the description of project $id.")
      )

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
