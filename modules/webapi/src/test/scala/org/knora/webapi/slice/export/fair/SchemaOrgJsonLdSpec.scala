/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import org.junit.runner.RunWith
import zio.json.*
import zio.json.ast.Json
import zio.test.*

import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest

@RunWith(classOf[DspZTestJUnitRunner])
class SchemaOrgJsonLdSpec extends ZIOSpecDefault with GoldenTest {

  private val orcid = "https://orcid.org/0000-0002-1825-0097"

  private val openWithFile = ResourceFairGraph(
    ark = "https://ark.dasch.swiss/ark:/72163/1/0868/abc123",
    pageUrl = "https://app.dasch.swiss/resource/0868/abc123",
    title = "Table 1",
    creators = Seq(
      Creator("Jane Doe", CreatorKind.Person, Some(orcid)),
      Creator("John Roe", CreatorKind.Person, None),
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

  private val restricted = openWithFile.copy(accessLevel = AccessLevel.Restricted, file = None)

  private val versioned = openWithFile.copy(
    ark = "https://ark.dasch.swiss/ark:/72163/1/0868/abc123.20240301T101530Z",
    file = None,
  )

  private val bare = openWithFile.copy(
    creators = Seq.empty,
    license = None,
    copyrightHolder = None,
    dateModified = None,
    file = None,
  )

  private def obj(j: Json, key: String): Option[Json] = j.asObject.flatMap(_.get(key))
  private def arr(j: Option[Json]): Option[Seq[Json]] = j.flatMap(_.asArray).map(_.toSeq)

  val spec: Spec[Any, Nothing] = suite("SchemaOrgJsonLd")(
    suite("golden")(
      test("open resource with a file")(assertGolden(SchemaOrgJsonLd.toJsonString(openWithFile), "openWithFile")),
      test("restricted resource")(assertGolden(SchemaOrgJsonLd.toJsonString(restricted), "restricted")),
      test("versioned ark")(assertGolden(SchemaOrgJsonLd.toJsonString(versioned), "versioned")),
      test("no license and no creators")(assertGolden(SchemaOrgJsonLd.toJsonString(bare), "bare")),
    ),
    suite("shape")(
      test("identifier has exactly the ARK PropertyValue and the page URL") {
        val ids = arr(obj(SchemaOrgJsonLd.render(openWithFile), "identifier"))
        assertTrue(
          ids.map(_.size) == Some(2),
          ids.flatMap(_.headOption).flatMap(obj(_, "propertyID")) == Some(Json.Str("ARK")),
          ids.flatMap(_.headOption).flatMap(obj(_, "value")) == Some(Json.Str(openWithFile.ark)),
          ids.flatMap(_.lift(1)) == Some(Json.Str(openWithFile.pageUrl)),
        )
      },
      test("license is an @id object, and absent when none") {
        val with_    = SchemaOrgJsonLd.render(openWithFile)
        val without_ = SchemaOrgJsonLd.render(bare)
        assertTrue(
          obj(with_, "license") == Some(Json.Obj("@id" -> Json.Str("https://creativecommons.org/licenses/by/4.0/"))),
          obj(without_, "license").isEmpty,
        )
      },
      test("distribution is a one-element array with a file, absent without") {
        val with_ = arr(obj(SchemaOrgJsonLd.render(openWithFile), "distribution"))
        assertTrue(
          with_.map(_.size) == Some(1),
          with_.flatMap(_.headOption).flatMap(obj(_, "license")) ==
            Some(Json.Obj("@id" -> Json.Str("https://creativecommons.org/publicdomain/zero/1.0/"))),
          obj(SchemaOrgJsonLd.render(restricted), "distribution").isEmpty,
        )
      },
      test("prov:wasAttributedTo holds only DaSCH and ORCID creators") {
        val ids = arr(obj(SchemaOrgJsonLd.render(openWithFile), "prov:wasAttributedTo"))
        assertTrue(
          ids == Some(
            Seq(Json.Obj("@id" -> Json.Str("https://dasch.swiss")), Json.Obj("@id" -> Json.Str(orcid))),
          ),
        )
      },
      test("a single creator is still an array") {
        val g = openWithFile.copy(creators = Seq(Creator("Jane Doe", CreatorKind.Person, Some(orcid))))
        assertTrue(arr(obj(SchemaOrgJsonLd.render(g), "creator")).map(_.size) == Some(1))
      },
      test("access properties follow the access level") {
        val r = SchemaOrgJsonLd.render(restricted)
        assertTrue(
          obj(r, "isAccessibleForFree") == Some(Json.Bool(false)),
          obj(r, "conditionsOfAccess") == Some(Json.Str("Open Access with Restrictions")),
        )
      },
      test("output parses as JSON and contains no null") {
        val outputs = Seq(openWithFile, restricted, versioned, bare).map(SchemaOrgJsonLd.toJsonString)
        assertTrue(outputs.forall(o => o.fromJson[Json].isRight && !o.contains("null")))
      },
    ),
  )
}
