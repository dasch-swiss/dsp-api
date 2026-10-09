/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.writers

import org.junit.runner.RunWith
import zio.Chunk
import zio.json.*
import zio.json.ast.Json
import zio.test.*

import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.fair.domain.*

@RunWith(classOf[DspZTestJUnitRunner])
class SchemaOrgJsonLdSpec extends ZIOSpecDefault {

  private val ark        = "https://ark.example.org/ark:/72163/1/0803/0b03b0a6e6bX"
  private val projectArk = "https://ark.example.org/ark:/72163/1/0803"
  private val pageUrl    = "https://app.example.org/resource/0803/0b03b0a6e6be"
  private val ccByUri    = "https://creativecommons.org/licenses/by/4.0/"
  private val classIri   = "http://api.example.org/ontology/0803/x/v2#Thing"

  private val file    = FileFacts("https://ingest.example.org/a/original", Some("image/jp2"), Some("a.jp2"), Some(42L))
  private val licence =
    ResourceLicence(Some(ccByUri), "Creative Commons Attribution 4.0 International", Some("CC-BY-4.0"))

  private val base = ResourceFacts(
    iri = ResourceIri.unsafeFrom("http://rdfh.ch/0803/0b03b0a6e6be"),
    ark = ark,
    pageUrl = pageUrl,
    title = "A resource",
    description = Some("A description"),
    creators = Creators(List("Ada Lovelace", "Alan Turing"), CreatorSource.Authorship),
    licence = Some(licence),
    dateCreated = Instant.parse("2024-01-02T03:04:05Z"),
    dateModified = Some(Instant.parse("2025-01-02T03:04:05Z")),
    typeOfData = Some("Image"),
    classIri = classIri,
    classLabel = "Thing",
    file = Some(file),
    accessLevel = AccessLevel.FullOpenAccess,
    projectArk = projectArk,
    shortcode = Shortcode.unsafeFrom("0803"),
  )

  private def obj(json: Json): Chunk[(String, Json)] = json match {
    case Json.Obj(fields) => fields
    case other            => throw new AssertionError(s"not an object: $other")
  }
  private def keys(json: Json): List[String]             = obj(json).map(_._1).toList
  private def get(json: Json, key: String): Option[Json] = obj(json).collectFirst { case (k, v) if k == key => v }
  private def render(f: ResourceFacts): Json             = SchemaOrgJsonLd.render(f)
  private def idRef(id: String): Json                    = Json.Obj(Chunk("@id" -> Json.Str(id)))

  override def spec: Spec[Any, Any] = suite("SchemaOrgJsonLd")(
    test("the resource is a Dataset identified by its ARK, with a PropertyValue and the page URL as identifier") {
      val doc = render(base)
      assertTrue(
        get(doc, "@type").contains(Json.Str("Dataset")),
        get(doc, "@id").contains(Json.Str(ark)),
        get(doc, "identifier").contains(
          Json.Arr(
            Json.Obj(
              Chunk(
                "@type"      -> Json.Str("PropertyValue"),
                "propertyID" -> Json.Str("ARK"),
                "value"      -> Json.Str(ark),
              ),
            ),
            Json.Str(pageUrl),
          ),
        ),
      )
    },
    test("the context binds the prov namespace") {
      assertTrue(
        get(render(base), "@context").contains(
          Json.Arr(Json.Str("https://schema.org"), Json.Obj(Chunk("prov" -> Json.Str("http://www.w3.org/ns/prov#")))),
        ),
      )
    },
    test("the licence is an @id node at the root and in the DataDownload") {
      val doc = render(base)
      assertTrue(
        get(doc, "license").contains(idRef(ccByUri)),
        get(doc, "distribution").flatMap(get(_, "license")).contains(idRef(ccByUri)),
      )
    },
    test("a declaration or a missing licence yields no license key") {
      val declaration = base.copy(licence = Some(ResourceLicence(None, "All rights reserved", None)))
      val none        = base.copy(licence = None)
      assertTrue(
        List(declaration, none).forall { f =>
          val doc = render(f)
          get(doc, "license").isEmpty && get(doc, "distribution").exists(get(_, "license").isEmpty)
        },
      )
    },
    test("unrecorded facts yield no key and no placeholder") {
      val bare = base.copy(
        description = None,
        dateModified = None,
        licence = None,
        file = None,
        creators = Creators(List("Project"), CreatorSource.ProjectOrganisation),
      )
      val ks = keys(render(bare))
      assertTrue(
        List("description", "dateModified", "license", "distribution", "creator").forall(k => !ks.contains(k)),
        !render(bare).toString.contains("null"),
      )
    },
    test("publisher and attribution are constants") {
      val doc = render(base)
      assertTrue(
        get(doc, "publisher").contains(
          Json.Obj(
            Chunk(
              "@id"   -> Json.Str("https://dasch.swiss"),
              "@type" -> Json.Str("Organization"),
              "name"  -> Json.Str("DaSCH"),
              "url"   -> Json.Str("https://dasch.swiss"),
            ),
          ),
        ),
        get(doc, "prov:wasAttributedTo").contains(idRef("https://dasch.swiss")),
      )
    },
    test("top-level keys keep their order") {
      assertTrue(
        keys(render(base)) == List(
          "@context",
          "@type",
          "@id",
          "identifier",
          "name",
          "description",
          "license",
          "isAccessibleForFree",
          "conditionsOfAccess",
          "datePublished",
          "dateCreated",
          "dateModified",
          "creator",
          "publisher",
          "prov:wasAttributedTo",
          "url",
          "isPartOf",
          "additionalType",
          "distribution",
        ),
      )
    },
    test("access level drives both access properties") {
      val full       = render(base)
      val restricted = render(base.copy(accessLevel = AccessLevel.OpenAccessWithRestrictions))
      assertTrue(
        get(full, "isAccessibleForFree").contains(Json.Bool(true)),
        get(full, "conditionsOfAccess").contains(Json.Str("Full Open Access")),
        get(restricted, "isAccessibleForFree").contains(Json.Bool(false)),
        get(restricted, "conditionsOfAccess").contains(Json.Str("Open Access with Restrictions")),
      )
    },
    test("the file is one DataDownload with a numeric contentSize in a fixed key order") {
      val dist = get(render(base), "distribution").get
      assertTrue(
        keys(dist) == List("@type", "contentUrl", "name", "encodingFormat", "contentSize", "license"),
        get(dist, "contentSize").contains(Json.Num(42L)),
        render(base).toJson.contains("\"contentSize\":42"),
        get(dist, "contentUrl").contains(Json.Str(file.contentUrl)),
      )
    },
    test("file fields that are not recorded are omitted") {
      val dist = get(render(base.copy(file = Some(FileFacts(file.contentUrl, None, None, None)))), "distribution").get
      assertTrue(keys(dist) == List("@type", "contentUrl", "license"))
    },
    test("one creator is a bare object and several are an array") {
      val one  = render(base.copy(creators = Creators(List("Ada Lovelace"), CreatorSource.ProjectDefault)))
      val many = render(base)
      assertTrue(
        get(one, "creator").contains(
          Json.Obj(Chunk("@type" -> Json.Str("Person"), "name" -> Json.Str("Ada Lovelace"))),
        ),
        get(many, "creator").exists {
          case Json.Arr(items) => items.size == 2
          case _               => false
        },
      )
    },
    test("the resource is part of its project and typed by its class") {
      val doc = render(base)
      assertTrue(
        get(doc, "isPartOf").contains(idRef(projectArk)),
        get(doc, "additionalType").contains(Json.Str(classIri)),
        get(doc, "url").contains(Json.Str(pageUrl)),
      )
    },
  )
}
