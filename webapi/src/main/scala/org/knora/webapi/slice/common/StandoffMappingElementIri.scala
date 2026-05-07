/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import scala.util.matching.Regex

import dsp.valueobjects.UuidUtil
import org.knora.webapi.slice.common.Value.StringValue

/**
 * IRI of an element of a standoff (XML→standoff) mapping — for example a tag,
 * an attribute, or a data type sub-node. Produced by
 * [[StandoffMappingElementIri.makeNew]] and persisted as the subject of the
 * mapping element triples. Has the form
 * `<mappingIri>/elements/<base64uuid>` where `<mappingIri>` is a valid
 * [[StandoffMappingIri]] and `<base64uuid>` is a 22-char URL-safe Base64
 * encoding of a random UUID.
 */
final case class StandoffMappingElementIri private (override val value: String) extends StringValue

object StandoffMappingElementIri extends StringValueCompanion[StandoffMappingElementIri] {

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
