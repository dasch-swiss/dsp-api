/*
 * Copyright © 2021 - 2022 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.project.domain

import zio.prelude.Validation

import java.util.UUID
import dsp.valueobjects.Project._
import dsp.valueobjects.Iri._

/**
 * Stores the project ID, i.e. UUID, IRI and shortcode of the project
 *
 * @param uuid      the UUID of the project
 * @param iri       the IRI of the project
 * @param shortcode the shortcode of the project
 */
abstract case class ProjectId private (
  uuid: UUID,
  iri: ProjectIri,
  shortcode: Shortcode
)

/**
 * Companion object for ProjectId. Contains factory methods for creating ProjectId instances.
 */
object ProjectId {

  // TODO: add docs for shortcode

  /**
   * Generates a ProjectId instance with a new (random) UUID and an IRI which is created from a prefix and the UUID.
   *
   * @return a new ProjectId instance
   */
  def fromIri(iri: ProjectIri, shortcode: Shortcode): ProjectId = {
    val uuid: UUID = UUID.fromString(iri.value.split("/").last)
    new ProjectId(uuid = uuid, iri = iri, shortcode = shortcode) {}
  }

  /**
   * Generates a ProjectId instance with a new (random) UUID and an IRI which is created from a prefix and the UUID.
   *
   * @return a new ProjectId instance
   */
  def fromUuid(uuid: UUID, shortcode: Shortcode): ProjectId = {
    val iri: ProjectIri = ProjectIri.make(s"http://rdfh.ch/projects/${uuid}").fold(e => throw e.head, v => v)
    new ProjectId(uuid = uuid, iri = iri, shortcode = shortcode) {}
  }

  /**
   * Generates a ProjectId instance with a new (random) UUID and an IRI which is created from a prefix and the UUID.
   *
   * @return a new ProjectId instance
   */
  def make(shortcode: Shortcode): ProjectId = {
    val uuid: UUID      = UUID.randomUUID()
    val iri: ProjectIri = ProjectIri.make(s"http://rdfh.ch/projects/${uuid}").fold(e => throw e.head, v => v)
    new ProjectId(uuid = uuid, iri = iri, shortcode = shortcode) {}
  }
}

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
) extends Ordered[Project] { self =>

  /**
   * Allows to sort collections of [[User]]s. Sorting is done by the IRI.
   */
  def compare(that: Project): Int = self.id.iri.toString().compareTo(that.id.iri.toString())

  // TODO: add this kind of things
//   def updateUsername(value: Username): User =
//     new User(self.id, self.givenName, self.familyName, value, self.email, self.password, self.language) {}
}
object Project {
  def make(
    id: ProjectId,
    name: String,
    description: String
  ): Project =
    new Project(id = id, name = name, description = description) {}

}
