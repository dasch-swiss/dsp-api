/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import scala.util.matching.Regex

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.Value.StringValue

/**
 * IRI of a standoff (XML→standoff) mapping.
 *
 * Two forms exist:
 *   - project-scoped: `http://rdfh.ch/projects/<projectId>/mappings/<name>`,
 *     created via `StringFormatter.makeProjectMappingIri`.
 *   - built-in: `http://rdfh.ch/standoff/mappings/<name>` — the only instances
 *     are `StandardMapping` and `TEIMapping` (see `OntologyConstants.KnoraBase`).
 */
final case class StandoffMappingIri private (
  override val value: String,
  projectIri: Option[ProjectIri],
  mappingName: String,
) extends StringValue {
  def isBuiltIn: Boolean = projectIri.isEmpty
}

object StandoffMappingIri extends StringValueCompanion[StandoffMappingIri] {

  private val MappingNameRegex: Regex = """[A-Za-z0-9_-]+""".r

  private val BuiltInMappingIriRegex: Regex =
    """^http://rdfh\.ch/standoff/mappings/([A-Za-z0-9_-]+)$""".r

  private val ProjectMappingIriRegex: Regex =
    """^(http://rdfh\.ch/projects/[a-zA-Z0-9_-]{4,40})/mappings/([A-Za-z0-9_-]+)$""".r

  val StandardMapping: StandoffMappingIri = StandoffMappingIri.unsafeFrom(OntologyConstants.KnoraBase.StandardMapping)
  val TEIMapping: StandoffMappingIri      = StandoffMappingIri.unsafeFrom(OntologyConstants.KnoraBase.TEIMapping)

  def from(value: String): Either[String, StandoffMappingIri] = value match {
    case BuiltInMappingIriRegex(name) =>
      Right(StandoffMappingIri(value, None, name))
    case ProjectMappingIriRegex(projectIri, name) =>
      // safe: the regex above already ensures the project IRI segment is well-formed
      Right(StandoffMappingIri(value, Some(ProjectIri.unsafeFrom(projectIri)), name))
    case _ =>
      Left(s"<$value> is not a standoff mapping IRI")
  }

  def from(projectIri: ProjectIri, mappingName: String): Either[String, StandoffMappingIri] =
    mappingName match {
      case MappingNameRegex() => from(s"$projectIri/mappings/$mappingName")
      case _                  => Left(s"<$mappingName> is not a valid mapping name")
    }
}
