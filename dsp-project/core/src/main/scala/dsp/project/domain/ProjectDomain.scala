/*
 * Copyright © 2021 - 2022 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.domain

import dsp.valueobjects.Iri._
import dsp.valueobjects.Project._
import dsp.valueobjects._
import zio.prelude.Validation

import java.util.UUID
import dsp.errors.ValidationException

/**
 * Represents the project domain object.
 *
 * @param id          the ID of the project
 * @param name        the name of the project
 * @param description the description of the project
 */
sealed abstract case class Project private (
  id: ProjectId,
  name: String,
  description: String
  // TODO: add project status here
  // TODO: use project valueobjects for things like name, description, etc.
) extends Ordered[Project] { self =>

  /**
   * Allows to sort collections of [[Project]]s. Sorting is done by the IRI.
   */
  def compare(that: Project): Int = self.id.iri.toString().compareTo(that.id.iri.toString())
  // TODO: by which field should a project be sorted by? shortcode? name? IRI?

  /**
   * Update the name of the project.
   *
   * @param name the new name
   * @return the updated Project
   */
  def updateProjectName(name: String): Validation[ValidationException, Project] =
    Project.make(
      id = self.id,
      name = name,
      description = self.description
    )

  /**
   * Update the description of the project.
   *
   * @param description the new description
   * @return the updated Project
   */
  def updateProjectDescription(description: String): Validation[ValidationException, Project] =
    Project.make(
      id = self.id,
      name = self.name,
      description = description
    )
}
object Project {
  def make(
    id: ProjectId,
    name: String,       // TODO: make LangString as soon as we have it
    description: String // TODO: make LangString as soon as we have it
  ): Validation[ValidationException, Project] =
    Validation.succeed(new Project(id = id, name = name, description = description) {})

}
