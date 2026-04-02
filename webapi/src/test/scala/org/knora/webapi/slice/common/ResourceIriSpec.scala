/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import zio.test.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

object ResourceIriSpec extends ZIOSpecDefault {

  val spec = suite("ResourceIri")(
    suite("from(String)")(
      test("should return a ResourceIri for a valid resource IRI") {
        val iri    = "http://rdfh.ch/080C/Ef9heHjPWDS7dMR_gGax2Q"
        val result = ResourceIri.from(iri)
        assertTrue(
          result.isRight,
          result.toOption.get.value == iri,
          result.toOption.get.shortcode == Shortcode.unsafeFrom("080C"),
        )
      },
      test("should return a ResourceIri for various valid resource IRIs") {
        val validIris = Seq(
          "http://rdfh.ch/0803/7bbb8e59b703",
          "http://rdfh.ch/0001/thing_with_BCE_date",
          "http://rdfh.ch/0001/a-thing",
          "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ",
          "http://rdfh.ch/0001/thing-with-history",
        )
        check(Gen.fromIterable(validIris)) { iri =>
          assertTrue(ResourceIri.from(iri).isRight)
        }
      },
      test("should fail for an ontology entity IRI") {
        val invalidIris = Seq(
          "http://example.com/ontology#Foo",
          "http://www.knora.org/ontology/0001/anything#Thing",
          "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
          "http://www.knora.org/ontology/0001/anything#hasListItem",
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem",
        )
        check(Gen.fromIterable(invalidIris)) { iri =>
          assertTrue(ResourceIri.from(iri) == Left(s"<$iri> is not a Knora resource IRI"))
        }
      },
      test("should fail for a value IRI") {
        val valueIri = "http://rdfh.ch/0001/thing-with-history/values/xZisRC3jPkcplt1hQQdb-A"
        assertTrue(ResourceIri.from(valueIri) == Left(s"<$valueIri> is not a Knora resource IRI"))
      },
      test("should fail for an empty string") {
        assertTrue(ResourceIri.from("") == Left("<> is not a Knora resource IRI"))
      },
      test("should fail for a wrong domain") {
        assertTrue(ResourceIri.from("http://example.com/0001/abc123").isLeft)
      },
      test("should fail for an invalid shortcode") {
        assertTrue(ResourceIri.from("http://rdfh.ch/ZZZZ/abc123").isLeft)
      },
    ),
    suite("unsafeFrom")(
      test("should return a ResourceIri for a valid IRI") {
        val iri = "http://rdfh.ch/080C/Ef9heHjPWDS7dMR_gGax2Q"
        assertTrue(ResourceIri.unsafeFrom(iri).value == iri)
      },
      test("should throw for an invalid IRI") {
        assertTrue(
          try {
            ResourceIri.unsafeFrom("invalid")
            false
          } catch { case _: IllegalArgumentException => true },
        )
      },
    ),
  )
}
