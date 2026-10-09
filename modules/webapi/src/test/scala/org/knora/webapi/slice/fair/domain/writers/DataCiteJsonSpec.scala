/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.writers

import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonMetaSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.NonValidationKeyword
import com.networknt.schema.SchemaValidatorsConfig
import com.networknt.schema.SpecVersion
import org.junit.runner.RunWith
import zio.Chunk
import zio.json.*
import zio.json.ast.Json
import zio.test.*

import java.time.Instant
import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.fair.domain.*

@RunWith(classOf[DspZTestJUnitRunner])
class DataCiteJsonSpec extends ZIOSpecDefault {

  private val ark        = "https://ark.example.org/ark:/72163/1/0803/0b03b0a6e6bX"
  private val projectArk = "https://ark.example.org/ark:/72163/1/0803"
  private val ccByUri    = "https://creativecommons.org/licenses/by/4.0/"

  private val file    = FileFacts("https://ingest.example.org/a/original", Some("image/jp2"), Some("a.jp2"), Some(42L))
  private val licence =
    ResourceLicence(Some(ccByUri), "Creative Commons Attribution 4.0 International", Some("CC-BY-4.0"))

  private val base = ResourceFacts(
    iri = ResourceIri.unsafeFrom("http://rdfh.ch/0803/0b03b0a6e6be"),
    ark = ark,
    pageUrl = "https://app.example.org/resource/0803/0b03b0a6e6be",
    title = "A resource",
    description = Some("A description"),
    creators = Creators(List("Ada Lovelace", "Alan Turing"), CreatorSource.Authorship),
    licence = Some(licence),
    dateCreated = Instant.parse("2024-01-02T03:04:05Z"),
    dateModified = Some(Instant.parse("2025-01-02T03:04:05Z")),
    typeOfData = Some("Image"),
    classIri = "http://api.example.org/ontology/0803/x/v2#Thing",
    classLabel = "Thing",
    file = Some(file),
    accessLevel = AccessLevel.FullOpenAccess,
    projectArk = projectArk,
    shortcode = Shortcode.unsafeFrom("0803"),
  )

  private val minimal = base.copy(
    description = None,
    licence = None,
    dateModified = None,
    typeOfData = None,
    file = None,
    creators = Creators(List("Project"), CreatorSource.ProjectOrganisation),
  )

  private def str(s: String): Json             = Json.Str(s)
  private def o(fields: (String, Json)*): Json = Json.Obj(Chunk.from(fields))

  private def get(json: Json, key: String): Option[Json] = json match {
    case Json.Obj(fields) => fields.collectFirst { case (k, v) if k == key => v }
    case _                => None
  }
  private def keys(json: Json): List[String] = json match {
    case Json.Obj(fields) => fields.map(_._1).toList
    case _                => Nil
  }
  private def render(f: ResourceFacts): Json = DataCiteJson.render(f)

  private def hasEmpty(json: Json): Boolean = json match {
    case Json.Null       => true
    case Json.Arr(items) => items.isEmpty || items.exists(hasEmpty)
    case Json.Obj(flds)  => flds.isEmpty || flds.exists(f => hasEmpty(f._2))
    case _               => false
  }

  private val schema = {
    val config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(false).build()
    // The vendored schema carries a draft-04 style "id" keyword, which draft-07 validators reject unless ignored.
    val draft7  = JsonMetaSchema.builder(JsonMetaSchema.getV7).keyword(new NonValidationKeyword("id")).build()
    val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7, b => b.metaSchema(draft7): Unit)
    factory.getSchema(
      getClass.getResourceAsStream("/org/knora/webapi/slice/fair/domain/writers/datacite-4.3-schema.json"),
      config,
    )
  }

  private def violations(f: ResourceFacts): List[String] =
    schema.validate(new ObjectMapper().readTree(render(f).toJson)).asScala.map(_.getMessage).toList

  private val baseKeys = List(
    "identifiers",
    "types",
    "creators",
    "titles",
    "publisher",
    "publicationYear",
    "descriptions",
    "dates",
    "relatedIdentifiers",
    "rightsList",
    "formats",
    "sizes",
    "schemaVersion",
  )

  override def spec: Spec[Any, Any] = suite("DataCiteJson")(
    test("top-level keys follow the kernel order with schemaVersion last") {
      assertTrue(keys(render(base)) == baseKeys)
    },
    test("the ARK is the identifier and the class label the resource type") {
      val doc = render(base)
      assertTrue(
        get(doc, "identifiers").contains(
          Json.Arr(o("identifier" -> str(ark), "identifierType" -> str("ARK"))),
        ),
        get(doc, "types").contains(o("resourceType" -> str("Thing"), "resourceTypeGeneral" -> str("Image"))),
        get(render(minimal), "types")
          .contains(o("resourceType" -> str("Thing"), "resourceTypeGeneral" -> str("Dataset"))),
      )
    },
    test("authorship and project-default creators are personal") {
      val expected = Json.Arr(
        o("name" -> str("Ada Lovelace"), "nameType" -> str("Personal")),
        o("name" -> str("Alan Turing"), "nameType"  -> str("Personal")),
      )
      val default = base.copy(creators = base.creators.copy(source = CreatorSource.ProjectDefault))
      assertTrue(get(render(base), "creators").contains(expected), get(render(default), "creators").contains(expected))
    },
    test("the project fallback creator is organizational") {
      assertTrue(
        get(render(minimal), "creators").contains(
          Json.Arr(o("name" -> str("Project"), "nameType" -> str("Organizational"))),
        ),
      )
    },
    test("title, publisher and publication year") {
      val doc = render(base)
      assertTrue(
        get(doc, "titles").contains(Json.Arr(o("title" -> str("A resource")))),
        get(doc, "publisher").contains(str("DaSCH")),
        get(doc, "publicationYear").contains(str("2024")),
      )
    },
    test("a description is an Abstract and is omitted when absent") {
      assertTrue(
        get(render(base), "descriptions").contains(
          Json.Arr(o("description" -> str("A description"), "descriptionType" -> str("Abstract"))),
        ),
        get(render(minimal), "descriptions").isEmpty,
      )
    },
    test("dates are Created, Updated when modified, then Available") {
      val dated = (d: String, t: String) => o("date" -> str(d), "dateType" -> str(t))
      assertTrue(
        get(render(base), "dates").contains(
          Json.Arr(
            dated("2024-01-02T03:04:05Z", "Created"),
            dated("2025-01-02T03:04:05Z", "Updated"),
            dated("2024-01-02T03:04:05Z", "Available"),
          ),
        ),
        get(render(minimal), "dates").contains(
          Json.Arr(dated("2024-01-02T03:04:05Z", "Created"), dated("2024-01-02T03:04:05Z", "Available")),
        ),
      )
    },
    test("the resource is part of the project ARK") {
      assertTrue(
        get(render(base), "relatedIdentifiers").contains(
          Json.Arr(
            o(
              "relatedIdentifier"     -> str(projectArk),
              "relatedIdentifierType" -> str("ARK"),
              "relationType"          -> str("IsPartOf"),
            ),
          ),
        ),
      )
    },
    test("a CC licence carries its URI and SPDX identifier") {
      assertTrue(
        get(render(base), "rightsList").contains(
          Json.Arr(
            o(
              "rights"                 -> str("Creative Commons Attribution 4.0 International"),
              "rightsUri"              -> str(ccByUri),
              "rightsIdentifier"       -> str("CC-BY-4.0"),
              "rightsIdentifierScheme" -> str("SPDX"),
            ),
          ),
        ),
      )
    },
    test("a non-CC licence has rights and rightsUri only") {
      val other = base.copy(licence = Some(ResourceLicence(Some("https://example.org/l"), "Other", None)))
      assertTrue(
        get(render(other), "rightsList").contains(
          Json.Arr(o("rights" -> str("Other"), "rightsUri" -> str("https://example.org/l"))),
        ),
      )
    },
    test("a declaration has the rights label only, and no licence has no rightsList") {
      val declared = base.copy(licence = Some(ResourceLicence(None, "Declared", None)))
      assertTrue(
        get(render(declared), "rightsList").contains(Json.Arr(o("rights" -> str("Declared")))),
        get(render(minimal), "rightsList").isEmpty,
      )
    },
    test("formats and sizes appear only when recorded") {
      val bare = base.copy(file = Some(FileFacts("u", None, None, None)))
      assertTrue(
        get(render(base), "formats").contains(Json.Arr(str("image/jp2"))),
        get(render(base), "sizes").contains(Json.Arr(str("42 bytes"))),
        get(render(bare), "formats").isEmpty,
        get(render(bare), "sizes").isEmpty,
      )
    },
    test("a minimal resource emits no null, empty array or empty object") {
      assertTrue(!hasEmpty(render(minimal)))
    },
    test("a resource with a file validates against the DataCite 4.3 schema") {
      val v = violations(base)
      assertTrue(v.isEmpty) ?? v.mkString("; ")
    },
    test("a resource without a file validates against the DataCite 4.3 schema") {
      val v = violations(base.copy(file = None))
      assertTrue(v.isEmpty) ?? v.mkString("; ")
    },
    test("a resource whose creator falls back to the project validates against the DataCite 4.3 schema") {
      val v = violations(minimal)
      assertTrue(v.isEmpty) ?? v.mkString("; ")
    },
  )
}
