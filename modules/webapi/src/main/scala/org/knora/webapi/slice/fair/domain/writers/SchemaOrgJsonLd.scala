/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.writers

import zio.Chunk
import zio.json.ast.Json

import org.knora.webapi.slice.fair.domain.AccessLevel
import org.knora.webapi.slice.fair.domain.FileFacts
import org.knora.webapi.slice.fair.domain.ResourceFacts
import org.knora.webapi.slice.fair.domain.writers.WriterFacts.*

/** The schema.org JSON-LD description of one resource. Keys appear in a fixed order; unrecorded facts yield no key. */
object SchemaOrgJsonLd {

  private val provNamespace = "http://www.w3.org/ns/prov#"

  def render(facts: ResourceFacts): Json =
    obj(
      field("@context", Json.Arr(Json.Str("https://schema.org"), obj(field("prov", Json.Str(provNamespace))))),
      field("@type", Json.Str("Dataset")),
      field("@id", Json.Str(facts.ark)),
      field("identifier", identifier(facts)),
      field("name", Json.Str(facts.title)),
      optField("description", facts.description.map(Json.Str(_))),
      optField("license", facts.licence.flatMap(_.uri).map(idRef)),
      field("isAccessibleForFree", Json.Bool(facts.accessLevel == AccessLevel.FullOpenAccess)),
      field("conditionsOfAccess", Json.Str(facts.accessLevel.text)),
      field("datePublished", Json.Str(facts.dateCreated.toString)),
      field("dateCreated", Json.Str(facts.dateCreated.toString)),
      optField("dateModified", facts.dateModified.map(i => Json.Str(i.toString))),
      optField("creator", creators(facts)),
      field("publisher", publisher),
      field("prov:wasAttributedTo", idRef(PublisherUrl)),
      field("url", Json.Str(facts.pageUrl)),
      field("isPartOf", idRef(facts.projectArk)),
      field("additionalType", Json.Str(facts.classIri)),
      optField("distribution", facts.file.map(distribution(facts, _))),
    )

  private def identifier(facts: ResourceFacts): Json =
    Json.Arr(
      obj(
        field("@type", Json.Str("PropertyValue")),
        field("propertyID", Json.Str("ARK")),
        field("value", Json.Str(facts.ark)),
      ),
      Json.Str(facts.pageUrl),
    )

  private def idRef(id: String): Json = obj(field("@id", Json.Str(id)))

  private def publisher: Json =
    obj(
      field("@id", Json.Str(PublisherUrl)),
      field("@type", Json.Str("Organization")),
      field("name", Json.Str(PublisherName)),
      field("url", Json.Str(PublisherUrl)),
    )

  private def creators(facts: ResourceFacts): Option[Json] =
    personCreators(facts).map(n => obj(field("@type", Json.Str("Person")), field("name", Json.Str(n)))) match {
      case Nil        => None
      case one :: Nil => Some(one)
      case many       => Some(Json.Arr(Chunk.from(many)))
    }

  private def distribution(facts: ResourceFacts, file: FileFacts): Json =
    obj(
      field("@type", Json.Str("DataDownload")),
      field("contentUrl", Json.Str(file.contentUrl)),
      optField("name", file.fileName.map(Json.Str(_))),
      optField("encodingFormat", file.mimeType.map(Json.Str(_))),
      optField("contentSize", file.size.map(Json.Num(_))),
      optField("license", facts.licence.flatMap(_.uri).map(idRef)),
    )
}
