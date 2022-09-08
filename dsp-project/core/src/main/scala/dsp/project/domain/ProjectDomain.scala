/*
 * Copyright © 2021 - 2022 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.domain

import zio.prelude.Validation

import dsp.errors.ValidationException
import dsp.valueobjects.Project._
import dsp.valueobjects._

/**
 * Represents the project domain object.
 *
 * @param id          the ID of the project
 * @param name        the name of the project
 * @param description the description of the project
 */
sealed abstract case class Project private (
  id: ProjectId,
  name: Name,
  description: ProjectDescription
  // TODO-BL: [domain-model] missing status, shortname, selfjoin
) extends Ordered[Project] { self =>

  /**
   * Allows to sort collections of [[Project]]s. Sorting is done by the IRI.
   */
  def compare(that: Project): Int = self.id.iri.toString().compareTo(that.id.iri.toString())

  /**
   * Updates the name of the project.
   *
   * @param name the new name
   * @return the updated Project or a ValidationException
   */
  def updateProjectName(name: Name): Validation[ValidationException, Project] =
    Project.make(
      id = self.id,
      name = name,
      description = self.description
    )

  /**
   * Updates the description of the project.
   *
   * @param description the new description
   * @return the updated Project or a ValidationException
   */
  def updateProjectDescription(description: ProjectDescription): Validation[ValidationException, Project] =
    Project.make(
      id = self.id,
      name = self.name,
      description = description
    )
}
object Project {
  def make(
    id: ProjectId,
    name: Name,
    description: ProjectDescription
  ): Validation[ValidationException, Project] =
    Validation.succeed(new Project(id = id, name = name, description = description) {})

}
