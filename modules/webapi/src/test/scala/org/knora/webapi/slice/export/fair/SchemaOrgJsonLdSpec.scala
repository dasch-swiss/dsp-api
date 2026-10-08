/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import org.junit.runner.RunWith
import zio.json.*
import zio.json.ast.Json
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest

@RunWith(classOf[DspZTestJUnitRunner])
class SchemaOrgJsonLdSpec extends ZIOSpecDefault with GoldenTest {

  import FairGraphFixtures.*

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
        val g = openWithFile.copy(creators = Seq(Creator("Jane Doe", Some(CreatorKind.Person), Some(orcid))))
        assertTrue(arr(obj(SchemaOrgJsonLd.render(g), "creator")).map(_.size) == Some(1))
      },
      test("access properties follow the access level") {
        val r = SchemaOrgJsonLd.render(restricted)
        assertTrue(
          obj(r, "isAccessibleForFree") == Some(Json.Bool(false)),
          obj(r, "conditionsOfAccess") == Some(Json.Str("Open Access with Restrictions")),
        )
      },
      test("an unknown creator kind has no @type, a known one has") {
        val creators = arr(obj(SchemaOrgJsonLd.render(openWithFile), "creator")).getOrElse(Seq.empty)
        assertTrue(
          creators.flatMap(obj(_, "@type")) == Seq(Json.Str("Person")),
          creators.flatMap(obj(_, "name")) == Seq(Json.Str("Jane Doe"), Json.Str("John Roe")),
        )
      },
      test("output parses as JSON and contains no Json.Null") {
        val outputs = Seq(openWithFile, restricted, versioned, bare).map(SchemaOrgJsonLd.toJsonString(_).fromJson[Json])
        assertTrue(outputs.forall(_.exists(j => !containsNull(j))))
      },
    ),
  )
}
