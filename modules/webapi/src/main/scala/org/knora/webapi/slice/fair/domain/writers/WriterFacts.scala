/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.writers

import zio.Chunk
import zio.json.ast.Json

import java.time.ZoneOffset

import org.knora.webapi.slice.fair.domain.CreatorSource
import org.knora.webapi.slice.fair.domain.ResourceFacts

/** Values and helpers shared by the metadata writers. */
object WriterFacts {

  val PublisherName: String = "DaSCH"
  val PublisherUrl: String  = "https://dasch.swiss"

  /** The DataCite / Dublin Core general resource type. */
  def generalType(facts: ResourceFacts): String = facts.typeOfData match {
    case Some(t @ ("Image" | "Text" | "Audiovisual" | "Sound")) => t
    case _                                                      => "Dataset"
  }

  /** The DataCite nameType of the creators: [[CreatorSource.ProjectOrganisation]]'s single name is an organisation. */
  def nameType(source: CreatorSource): String = source match {
    case CreatorSource.Authorship | CreatorSource.ProjectDefault => "Personal"
    case CreatorSource.ProjectOrganisation                       => "Organizational"
  }

  /** Empty when the creators are an organisation. */
  def personCreators(facts: ResourceFacts): List[String] =
    if (nameType(facts.creators.source) == "Personal") facts.creators.names else List.empty

  /** The UTC year of creation. */
  def publicationYear(facts: ResourceFacts): String =
    facts.dateCreated.atZone(ZoneOffset.UTC).getYear.toString

  /** A field that is always present. */
  def field(key: String, value: Json): Option[(String, Json)] = Some(key -> value)

  /** A field that is dropped when its value is absent. */
  def optField(key: String, value: Option[Json]): Option[(String, Json)] = value.map(key -> _)

  /** An object whose key order is the order of the given fields, with absent fields dropped. */
  def obj(fields: Option[(String, Json)]*): Json.Obj = Json.Obj(Chunk.from(fields.flatten))
}
