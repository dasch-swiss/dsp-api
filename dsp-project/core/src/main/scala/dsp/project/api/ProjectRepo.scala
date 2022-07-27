/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.api

import dsp.errors._
import dsp.project.domain._
import zio._
import zio.macros.accessible

// import java.util.UUID

/**
 * The trait (interface) for the project repository. The project repository is responsible for storing and retrieving projects.
 * Needs to be used by the project repository implementations.
 */
@accessible // with this annotation we don't have to write the companion object ourselves
trait ProjectRepo {

  /**
   * Writes a project to the repository (used for both create and update).
   * If this fails (e.g. the triplestore is not available), it's a non-recovable error. That's why we need UIO.
   *   When used, we should do it like: ...store(...).orDie
   *
   * @param project the project to write
   * @return        The project ID
   */
  def storeProject(project: Project): UIO[ProjectId]

  /**
   * Gets all projects from the repository.
   *
   * @return   a list of [[Project]]
   */
  def getProjects(): UIO[List[Project]]

  /**
   * Retrieves the project from the repository by ID.
   *
   * @param id the project's ID
   * @return an optional [[Project]]
   */
  def getProjectById(id: ProjectId): IO[Option[Nothing], Project]

  /**
   * Retrieves the project from the repository by ShortCode.
   *
   * @param shortCode ShortCode of the project.
   * @return an optional [[Project]].
   */
  def getProjectByShortCode(shortCode: String): IO[Option[Nothing], Project]

  /**
   * Checks if a project ShortCode exists in the repo.
   *
   * @param shortCode ShortCode of the project.
   * @return Unit in case of success
   */
  def checkShortCodeExists(shortCode: String): IO[Option[Nothing], Unit]

  /**
   * Deletes a [[Project]] from the repository by its [[ProjectId]].
   *
   * @param id the project ID
   * @return   Project ID or None if not found
   */
  def deleteProject(id: ProjectId): IO[Option[Nothing], ProjectId]
}
