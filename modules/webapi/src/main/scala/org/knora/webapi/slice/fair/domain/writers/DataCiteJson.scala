/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.writers

import zio.Chunk
import zio.json.ast.Json

import org.knora.webapi.slice.fair.domain.ResourceFacts
import org.knora.webapi.slice.fair.domain.ResourceLicence
import org.knora.webapi.slice.fair.domain.writers.WriterFacts.*

/** The DataCite kernel-4 JSON description of one resource. Keys appear in kernel order; unrecorded facts yield no key. */
object DataCiteJson {

  private val schemaVersion = "http://datacite.org/schema/kernel-4"

  def render(facts: ResourceFacts): Json =
    obj(
      field("identifiers", arr(obj(field("identifier", str(facts.ark)), field("identifierType", str("ARK"))))),
      field("types", types(facts)),
      field("creators", creators(facts)),
      field("titles", arr(obj(field("title", str(facts.title))))),
      field("publisher", str(PublisherName)),
      field("publicationYear", str(publicationYear(facts))),
      optField("descriptions", facts.description.map(d => arr(typed("description", d, "descriptionType", "Abstract")))),
      field("dates", dates(facts)),
      field("relatedIdentifiers", relatedIdentifiers(facts)),
      optField("rightsList", facts.licence.map(l => arr(rights(l)))),
      optField("formats", facts.file.flatMap(_.mimeType).map(m => arr(str(m)))),
      optField("sizes", facts.file.flatMap(_.size).map(n => arr(str(s"$n bytes")))),
      field("schemaVersion", str(schemaVersion)),
    )

  private def str(s: String): Json = Json.Str(s)

  private def arr(items: Json*): Json = Json.Arr(Chunk.from(items))

  private def typed(valueKey: String, value: String, typeKey: String, typeValue: String): Json =
    obj(field(valueKey, str(value)), field(typeKey, str(typeValue)))

  private def types(facts: ResourceFacts): Json =
    obj(field("resourceType", str(facts.classLabel)), field("resourceTypeGeneral", str(generalType(facts))))

  private def creators(facts: ResourceFacts): Json = {
    val kind = nameType(facts.creators.source)
    Json.Arr(Chunk.from(facts.creators.names.map(n => typed("name", n, "nameType", kind))))
  }

  private def dates(facts: ResourceFacts): Json = {
    val created = facts.dateCreated.toString
    val updated = facts.dateModified.toList.map(m => typed("date", m.toString, "dateType", "Updated"))
    Json.Arr(
      Chunk.from(
        typed("date", created, "dateType", "Created") :: updated ::: List(
          typed("date", created, "dateType", "Available"),
        ),
      ),
    )
  }

  private def relatedIdentifiers(facts: ResourceFacts): Json =
    arr(
      obj(
        field("relatedIdentifier", str(facts.projectArk)),
        field("relatedIdentifierType", str("ARK")),
        field("relationType", str("IsPartOf")),
      ),
    )

  private def rights(l: ResourceLicence): Json =
    obj(
      field("rights", str(l.label)),
      optField("rightsUri", l.uri.map(str)),
      optField("rightsIdentifier", l.spdxId.map(str)),
      optField("rightsIdentifierScheme", l.spdxId.map(_ => str("SPDX"))),
    )
}
