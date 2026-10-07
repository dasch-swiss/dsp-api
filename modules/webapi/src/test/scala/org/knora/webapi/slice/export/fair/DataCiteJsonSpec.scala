/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import com.networknt.schema.InputFormat
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.junit.runner.RunWith
import zio.json.*
import zio.json.ast.Json
import zio.test.*

import java.time.Instant
import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest

@RunWith(classOf[DspZTestJUnitRunner])
class DataCiteJsonSpec extends ZIOSpecDefault with GoldenTest {

  private val orcid = "https://orcid.org/0000-0002-1825-0097"

  private val openWithFile = ResourceFairGraph(
    ark = "https://ark.dasch.swiss/ark:/72163/1/0868/abc123",
    pageUrl = "https://app.dasch.swiss/resource/0868/abc123",
    title = "Table 1",
    creators = Seq(
      Creator("Jane Doe", CreatorKind.Person, Some(orcid)),
      Creator("Example Institute", CreatorKind.Organization, None),
    ),
    dateCreated = Instant.parse("2024-03-01T10:15:30Z"),
    dateModified = Some(Instant.parse("2025-01-02T03:04:05Z")),
    license = Some("https://creativecommons.org/licenses/by/4.0/"),
    copyrightHolder = Some("University of Basel"),
    generalType = "Dataset",
    accessLevel = AccessLevel.FullOpen,
    file = Some(
      FileFacts(
        "https://ingest.dasch.swiss/projects/0868/assets/xyz/original",
        Some("table1.csv"),
        Some("text/csv"),
        Some(1234L),
        Some("https://creativecommons.org/publicdomain/zero/1.0/"),
      ),
    ),
    projectArk = "https://ark.dasch.swiss/ark:/72163/1/0868",
    projectShortcode = "0868",
    projectName = "Example project",
    resourceClassIri = "http://api.dasch.swiss/ontology/0868/example/v2#Table",
  )

  private val bare = openWithFile.copy(
    creators = Seq.empty,
    license = None,
    copyrightHolder = None,
    dateModified = None,
    file = None,
  )

  // The vendored schema is draft-07 but carries draft-04's `id`, which the validator rejects; the line is dropped, nothing else.
  private val schema = {
    val is  = getClass.getClassLoader.getResourceAsStream("org/knora/webapi/slice/export/fair/datacite-4.3-schema.json")
    val raw =
      try String(is.readAllBytes(), "UTF-8")
      finally is.close()
    JsonSchemaFactory
      .getInstance(SpecVersion.VersionFlag.V7)
      .getSchema(raw.linesIterator.filterNot(_.trim.startsWith("\"id\":")).mkString("\n"))
  }

  private def schemaErrors(g: ResourceFairGraph): Seq[String] =
    schema.validate(DataCiteJson.toJsonString(g), InputFormat.JSON).asScala.map(_.getMessage).toSeq

  private def field(j: Json, key: String): Option[Json] = j.asObject.flatMap(_.get(key))

  val spec: Spec[Any, Nothing] = suite("DataCiteJson")(
    suite("golden")(
      test("open resource with a file")(assertGolden(DataCiteJson.toJsonString(openWithFile), "openWithFile")),
      test("no license, creators or file")(assertGolden(DataCiteJson.toJsonString(bare), "bare")),
    ),
    suite("shape")(
      test("identifier is the ARK and creators carry their ORCID") {
        val j        = DataCiteJson.render(openWithFile)
        val creators = field(j, "creators").flatMap(_.asArray).map(_.toSeq).getOrElse(Seq.empty)
        assertTrue(
          field(j, "identifiers").flatMap(_.asArray).flatMap(_.headOption) ==
            Some(Json.Obj("identifier" -> Json.Str(openWithFile.ark), "identifierType" -> Json.Str("ARK"))),
          creators.flatMap(field(_, "nameType")) == Seq(Json.Str("Personal"), Json.Str("Organizational")),
          creators.headOption
            .flatMap(field(_, "nameIdentifiers"))
            .flatMap(_.asArray)
            .flatMap(_.headOption)
            .flatMap(field(_, "nameIdentifierScheme")) == Some(Json.Str("ORCID")),
          creators.lift(1).flatMap(field(_, "nameIdentifiers")).isEmpty,
        )
      },
      test("without creators DaSCH is the organizational creator") {
        val creators = field(DataCiteJson.render(bare), "creators").flatMap(_.asArray).map(_.toSeq)
        assertTrue(
          creators == Some(Seq(Json.Obj("name" -> Json.Str("DaSCH"), "nameType" -> Json.Str("Organizational")))),
        )
      },
      test("rightsList comes from the license only") {
        val with_ = field(DataCiteJson.render(openWithFile), "rightsList").flatMap(_.asArray).flatMap(_.headOption)
        assertTrue(
          with_.flatMap(field(_, "rightsIdentifier")) == Some(Json.Str("CC-BY-4.0")),
          with_.flatMap(field(_, "rightsIdentifierScheme")) == Some(Json.Str("SPDX")),
          with_.flatMap(field(_, "rightsUri")) == Some(Json.Str("https://creativecommons.org/licenses/by/4.0/")),
          field(DataCiteJson.render(bare), "rightsList").isEmpty,
        )
      },
      test("an unrecognised license carries no SPDX identifier") {
        val uri = "https://example.org/license"
        val r   = field(DataCiteJson.render(openWithFile.copy(license = Some(uri))), "rightsList")
          .flatMap(_.asArray)
          .flatMap(_.headOption)
        assertTrue(
          r.flatMap(field(_, "rightsUri")) == Some(Json.Str(uri)),
          r.flatMap(field(_, "rightsIdentifier")).isEmpty,
        )
      },
      test("formats only with an advertised file") {
        assertTrue(
          field(DataCiteJson.render(openWithFile), "formats") == Some(Json.Arr(Json.Str("text/csv"))),
          field(DataCiteJson.render(bare), "formats").isEmpty,
        )
      },
      test("publication year is the creation year, the project is related by IsPartOf") {
        val j = DataCiteJson.render(openWithFile)
        assertTrue(
          field(j, "publicationYear") == Some(Json.Str("2024")),
          field(j, "relatedIdentifiers").flatMap(_.asArray).flatMap(_.headOption).flatMap(field(_, "relationType")) ==
            Some(Json.Str("IsPartOf")),
        )
      },
      test("output contains no null") {
        assertTrue(
          !DataCiteJson.toJsonString(openWithFile).contains("null"),
          !DataCiteJson.toJsonString(bare).contains("null"),
        )
      },
    ),
    suite("DataCite 4.3 JSON schema")(
      test("the open resource validates")(assertTrue(schemaErrors(openWithFile).isEmpty)),
      test("the bare resource validates")(assertTrue(schemaErrors(bare).isEmpty)),
      test("the validator rejects a document without creators") {
        val broken = DataCiteJson.render(bare) match {
          case Json.Obj(fs) => Json.Obj(fs.filterNot(_._1 == "creators")).toJson
          case other        => other.toJson
        }
        assertTrue(schema.validate(broken, InputFormat.JSON).asScala.nonEmpty)
      },
    ),
  )
}
