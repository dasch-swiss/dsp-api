/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import zio.Chunk
import zio.json.*
import zio.json.ast.Json

/** Writes a [[ResourceFairGraph]] as the schema.org JSON-LD the DPE project page uses. Keys are emitted in insertion order. */
object SchemaOrgJsonLd {

  private val PublisherName = "DaSCH"
  private val PublisherUrl  = "https://dasch.swiss"
  private val ProvNamespace = "http://www.w3.org/ns/prov#"

  def toJsonString(g: ResourceFairGraph): String = render(g).toJsonPretty

  def render(g: ResourceFairGraph): Json = {
    val fields: Seq[Option[(String, Json)]] = Seq(
      Some("@context" -> Json.Arr(Json.Str("https://schema.org"), obj("prov" -> Json.Str(ProvNamespace)))),
      Some("@type"    -> Json.Str("Dataset")),
      Some("@id"      -> Json.Str(g.ark)),
      Some(
        "identifier" -> Json.Arr(
          obj("@type" -> Json.Str("PropertyValue"), "propertyID" -> Json.Str("ARK"), "value" -> Json.Str(g.ark)),
          Json.Str(g.pageUrl),
        ),
      ),
      Some("name"           -> Json.Str(g.title)),
      Some("additionalType" -> Json.Str(g.resourceClassIri)),
      g.license.map("license" -> licenseNode(_)),
      Some("isAccessibleForFree" -> Json.Bool(g.accessLevel == AccessLevel.FullOpen)),
      Some("conditionsOfAccess"  -> Json.Str(conditionsOfAccess(g.accessLevel))),
      Some("dateCreated"         -> Json.Str(g.dateCreated.toString)),
      g.dateModified.map(d => "dateModified" -> Json.Str(d.toString)),
      nonEmpty("creator", g.creators.map(agentNode)),
      g.copyrightHolder.map(n => "copyrightHolder" -> obj("@type" -> Json.Str("Organization"), "name" -> Json.Str(n))),
      Some(
        "publisher" -> obj(
          "@id"   -> Json.Str(PublisherUrl),
          "@type" -> Json.Str("Organization"),
          "name"  -> Json.Str(PublisherName),
          "url"   -> Json.Str(PublisherUrl),
        ),
      ),
      Some("prov:wasAttributedTo" -> Json.Arr(Chunk.from(attributedTo(g)))),
      Some("url"                  -> Json.Str(g.pageUrl)),
      Some(
        "isPartOf" -> obj(
          "@id"   -> Json.Str(g.projectArk),
          "@type" -> Json.Str("Dataset"),
          "name"  -> Json.Str(g.projectName),
        ),
      ),
      g.file.map(f => "distribution" -> Json.Arr(downloadNode(f))),
    )
    Json.Obj(Chunk.from(fields.flatten))
  }

  private def obj(fields: (String, Json)*): Json.Obj = Json.Obj(Chunk.from(fields))

  private def nonEmpty(key: String, values: Seq[Json]): Option[(String, Json)] =
    Option.when(values.nonEmpty)(key -> Json.Arr(Chunk.from(values)))

  // schema.org's context does not coerce `license` to @id, so a bare string would parse as a literal.
  private def licenseNode(uri: String): Json = obj("@id" -> Json.Str(uri))

  private def conditionsOfAccess(level: AccessLevel): String = level match {
    case AccessLevel.FullOpen   => "Full Open Access"
    case AccessLevel.Restricted => "Open Access with Restrictions"
  }

  private def agentNode(c: Creator): Json = {
    val kind = c.kind match {
      case CreatorKind.Person       => "Person"
      case CreatorKind.Organization => "Organization"
    }
    val id = c.orcid.map("@id" -> Json.Str(_))
    Json.Obj(Chunk.from(id.toSeq ++ Seq("@type" -> Json.Str(kind), "name" -> Json.Str(c.name))))
  }

  // References only: a creator without an ORCID has no node to point at.
  private def attributedTo(g: ResourceFairGraph): Seq[Json] =
    obj("@id" -> Json.Str(PublisherUrl)) +: g.creators.flatMap(_.orcid).map(o => obj("@id" -> Json.Str(o)))

  private def downloadNode(f: FileFacts): Json = {
    val fields: Seq[Option[(String, Json)]] = Seq(
      Some("@type"      -> Json.Str("DataDownload")),
      Some("contentUrl" -> Json.Str(f.contentUrl)),
      f.name.map("name" -> Json.Str(_)),
      f.encodingFormat.map("encodingFormat" -> Json.Str(_)),
      f.contentSize.map(s => "contentSize" -> Json.Num(BigDecimal(s).bigDecimal)),
      f.license.map("license" -> licenseNode(_)),
    )
    Json.Obj(Chunk.from(fields.flatten))
  }
}
