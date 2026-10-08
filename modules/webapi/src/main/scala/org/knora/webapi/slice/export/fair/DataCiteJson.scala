/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import zio.Chunk
import zio.json.*
import zio.json.ast.Json

import java.time.ZoneOffset

/**
 * Writes a [[ResourceFairGraph]] as a DataCite kernel-4 JSON document, the shape DPE serves at `/metadata.datacite.json`.
 * DataCite's schema types every member, so a property with no source is absent, never null or empty.
 */
object DataCiteJson {

  private val Publisher     = "DaSCH"
  private val SchemaVersion = "http://datacite.org/schema/kernel-4"
  private val OrcidScheme   = "https://orcid.org"

  def toJsonString(g: ResourceFairGraph): String = render(g).toJsonPretty

  // DataCite 4.3 requires `resourceType` as well as `resourceTypeGeneral`.
  def render(g: ResourceFairGraph): Json = {
    val fields: Seq[Option[(String, Json)]] = Seq(
      Some("identifiers"     -> Json.Arr(obj("identifier" -> str(g.ark), "identifierType" -> str("ARK")))),
      Some("types"           -> obj("resourceType" -> str(g.generalType), "resourceTypeGeneral" -> str(g.generalType))),
      Some("creators"        -> Json.Arr(Chunk.from(creators(g).map(creator)))),
      Some("titles"          -> Json.Arr(obj("title" -> str(g.title)))),
      Some("publisher"       -> str(Publisher)),
      Some("publicationYear" -> str(g.dateCreated.atZone(ZoneOffset.UTC).getYear.toString)),
      Some(
        "dates" -> Json.Arr(
          Chunk.from(
            Seq(obj("date" -> str(g.dateCreated.toString), "dateType" -> str("Created"))) ++
              g.dateModified.map(d => obj("date" -> str(d.toString), "dateType" -> str("Updated"))),
          ),
        ),
      ),
      Some(
        "relatedIdentifiers" -> Json.Arr(
          obj(
            "relatedIdentifier"     -> str(g.projectArk),
            "relatedIdentifierType" -> str("ARK"),
            "relationType"          -> str("IsPartOf"),
          ),
        ),
      ),
      g.license.map(l => "rightsList" -> Json.Arr(rights(l))),
      g.file.flatMap(_.encodingFormat).map(f => "formats" -> Json.Arr(str(f))),
      Some("schemaVersion" -> str(SchemaVersion)),
    )
    Json.Obj(Chunk.from(fields.flatten))
  }

  private def str(s: String): Json = Json.Str(s)

  private def obj(fields: (String, Json)*): Json.Obj = Json.Obj(Chunk.from(fields))

  // DataCite requires at least one creator; DaSCH stands in when none is recorded.
  private def creators(g: ResourceFairGraph): Seq[Creator] =
    if (g.creators.isEmpty) Seq(Creator(Publisher, Some(CreatorKind.Organization), None)) else g.creators

  private def creator(c: Creator): Json = {
    val nameType = c.kind.map {
      case CreatorKind.Person       => "nameType" -> str("Personal")
      case CreatorKind.Organization => "nameType" -> str("Organizational")
    }
    val identifiers = c.orcid.map { o =>
      "nameIdentifiers" -> Json.Arr(
        obj("nameIdentifier" -> str(o), "nameIdentifierScheme" -> str("ORCID"), "schemeUri" -> str(OrcidScheme)),
      )
    }
    Json.Obj(Chunk.from(Seq("name" -> str(c.name)) ++ nameType ++ identifiers))
  }

  private def rights(l: LicenseFact): Json =
    Json.Obj(
      Chunk.from(
        Seq("rights" -> str(l.label), "rightsUri" -> str(l.uri)) ++
          l.spdxId.toSeq.flatMap(id => Seq("rightsIdentifier" -> str(id), "rightsIdentifierScheme" -> str("SPDX"))),
      ),
    )
}
