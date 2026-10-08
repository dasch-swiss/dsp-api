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

import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest

@RunWith(classOf[DspZTestJUnitRunner])
class DataCiteJsonSpec extends ZIOSpecDefault with GoldenTest {

  import FairGraphFixtures.*

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
          creators.flatMap(field(_, "nameType")) == Seq(Json.Str("Personal")),
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
          with_.flatMap(field(_, "rights")) == Some(Json.Str("CC BY 4.0")),
          with_.flatMap(field(_, "rightsIdentifier")) == Some(Json.Str("CC-BY-4.0")),
          with_.flatMap(field(_, "rightsIdentifierScheme")) == Some(Json.Str("SPDX")),
          with_.flatMap(field(_, "rightsUri")) == Some(Json.Str("https://creativecommons.org/licenses/by/4.0/")),
          field(DataCiteJson.render(bare), "rightsList").isEmpty,
        )
      },
      test("a license without an SPDX id carries label and uri only") {
        val l = LicenseFact("https://example.org/license", "Example License", None)
        val r = field(DataCiteJson.render(openWithFile.copy(license = Some(l))), "rightsList")
          .flatMap(_.asArray)
          .flatMap(_.headOption)
        assertTrue(
          r.flatMap(field(_, "rights")) == Some(Json.Str("Example License")),
          r.flatMap(field(_, "rightsUri")) == Some(Json.Str(l.uri)),
          r.flatMap(field(_, "rightsIdentifier")).isEmpty,
          r.flatMap(field(_, "rightsIdentifierScheme")).isEmpty,
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
      test("an unknown creator kind has no nameType") {
        val creators =
          field(DataCiteJson.render(openWithFile.copy(creators = Seq(Creator("X", None, None)))), "creators")
        assertTrue(creators == Some(Json.Arr(Json.Obj("name" -> Json.Str("X")))))
      },
      test("output contains no Json.Null") {
        assertTrue(!containsNull(DataCiteJson.render(openWithFile)), !containsNull(DataCiteJson.render(bare)))
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
