/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import java.util.UUID
import scala.util.matching.Regex

import dsp.valueobjects.UuidUtil
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.Value.StringValue

trait StableIri extends StringValue

final case class PlaceholderIri private (override val value: String) extends StableIri

object PlaceholderIri extends WithFrom[String, PlaceholderIri] {
  val instance: PlaceholderIri = PlaceholderIri("urn:placeholder")

  def from(value: String): Either[String, PlaceholderIri] =
    if value == instance.value then Right(instance)
    else Left(s"<$value> is not a placeholder IRI.")
}

final case class ResourceId private (override val value: String) extends StableIri

object ResourceId extends WithFrom[String, ResourceId] {
  private val ResourceIdRegex: Regex = """^[A-Za-z0-9_-]+$""".r

  def from(value: String): Either[String, ResourceId] = value match {
    case ResourceIdRegex() => Right(ResourceId(value))
    case _                 => Left(s"<$value> is not a valid resource ID")
  }
}

final case class ResourceIri private (override val value: String, shortcode: Shortcode, resourceId: ResourceId)
    extends StableIri

object ResourceIri extends WithFrom[String, ResourceIri] {

  private val ResourceIriRegex: Regex =
    """^http://rdfh\.ch/(\p{XDigit}{4})/([A-Za-z0-9_-]+)$""".r

  def makeNew(shortcode: Shortcode): ResourceIri = {
    val id = UuidUtil.base64Encode(UUID.randomUUID)
    unsafeFrom(s"http://rdfh.ch/$shortcode/$id")
  }

  def from(value: String): Either[String, ResourceIri] = value match {
    case ResourceIriRegex(sc, id) =>
      // unsafe is safe here since the regex already ensures
      // the constraints for both code and id
      val shortcode  = Shortcode.unsafeFrom(sc)
      val resourceId = ResourceId.unsafeFrom(id)
      Right(ResourceIri(value, shortcode, resourceId))
    case _ => Left(s"<$value> is not a Knora resource IRI")
  }

  def from(iri: SmartIri): Either[String, ResourceIri] = from(iri.toString)
}

final case class ValueId private (override val value: String) extends StableIri

object ValueId extends WithFrom[String, ValueId] {
  private val ValueIdRegex: Regex = """^[A-Za-z0-9_-]+$""".r

  def from(value: String): Either[String, ValueId] = value match {
    case ValueIdRegex() => Right(ValueId(value))
    case _              => Left(s"<$value> is not a valid value ID")
  }
}

final case class ValueIri private (
  override val value: String,
  shortcode: Shortcode,
  resourceId: ResourceId,
  valueId: ValueId,
) extends StableIri {
  def sameResourceAs(other: ValueIri): Boolean =
    this.shortcode == other.shortcode && this.resourceId == other.resourceId
}

object ValueIri extends WithFrom[String, ValueIri] {

  private val ValueIriRegex: Regex =
    """^http://rdfh\.ch/(\p{XDigit}{4})/([A-Za-z0-9_-]+)/values/([A-Za-z0-9_-]+)$""".r

  def makeNew(resourceIri: ResourceIri): ValueIri = {
    val id = UuidUtil.base64Encode(UUID.randomUUID)
    unsafeFrom(s"http://rdfh.ch/${resourceIri.shortcode}/${resourceIri.resourceId}/values/$id")
  }

  def from(resourceIri: ResourceIri, uuid: UUID): ValueIri = {
    val id = UuidUtil.base64Encode(uuid)
    unsafeFrom(s"http://rdfh.ch/${resourceIri.shortcode}/${resourceIri.resourceId}/values/$id")
  }

  def from(value: String): Either[String, ValueIri] = value match {
    case ValueIriRegex(sc, resId, valId) =>
      // unsafe is safe here since the regex already ensures
      // the constraints for shortcode, resourceId, and valueId
      val shortcode  = Shortcode.unsafeFrom(sc)
      val resourceId = ResourceId.unsafeFrom(resId)
      val valueId    = ValueId.unsafeFrom(valId)
      Right(ValueIri(value, shortcode, resourceId, valueId))
    case _ => Left(s"<$value> is not a Knora value IRI")
  }

  def from(iri: SmartIri): Either[String, ValueIri] = from(iri.toString)
}

/**
 * IRI of an element of a standoff (XML→standoff) mapping — for example a tag,
 * an attribute, or a data type sub-node. Produced by
 * [[StandoffMappingElementIri.makeNew]] and persisted as the subject of the
 * mapping element triples. Has the form
 * `<mappingIri>/elements/<base64uuid>` where `<mappingIri>` is a valid
 * [[StandoffMappingIri]] and `<base64uuid>` is a 22-char URL-safe Base64
 * encoding of a random UUID.
 */
final case class StandoffMappingElementIri private (override val value: String) extends StableIri

object StandoffMappingElementIri extends WithFrom[String, StandoffMappingElementIri] {

  // Any non-empty path-segment characters after `/elements/`. In production the
  // segment is a 22-char URL-safe Base64 encoded UUID (see `makeNew`), but
  // mappings read back from the triplestore may carry legacy / hand-written
  // segments containing additional `/`-separated path parts.
  private val ElementSuffix: String = "[A-Za-z0-9_/-]+"

  private val BuiltInElementIriRegex: Regex =
    s"""^http://rdfh\\.ch/standoff/mappings/[A-Za-z0-9_-]+/elements/$ElementSuffix$$""".r

  private val ProjectElementIriRegex: Regex =
    s"""^http://rdfh\\.ch/projects/[a-zA-Z0-9_-]{4,40}/mappings/[A-Za-z0-9_-]+/elements/$ElementSuffix$$""".r

  def from(value: String): Either[String, StandoffMappingElementIri] = value match {
    case BuiltInElementIriRegex() | ProjectElementIriRegex() => Right(StandoffMappingElementIri(value))
    case _                                                   => Left(s"<$value> is not a standoff mapping element IRI")
  }

  def makeNew(mappingIri: StandoffMappingIri): StandoffMappingElementIri =
    StandoffMappingElementIri(s"$mappingIri/elements/${UuidUtil.makeRandomBase64EncodedUuid}")
}

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
) extends StableIri {
  def isBuiltIn: Boolean = projectIri.isEmpty
}

object StandoffMappingIri extends WithFrom[String, StandoffMappingIri] {

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
