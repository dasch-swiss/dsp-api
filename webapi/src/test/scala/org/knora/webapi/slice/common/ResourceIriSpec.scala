/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import zio.test.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

object ResourceIriSpec extends ZIOSpecDefault {

  private val validResourceIri = "http://rdfh.ch/080C/Ef9heHjPWDS7dMR_gGax2Q"

  val spec = suite("ResourceIri")(
    suite("from")(
      test("should return a ResourceIri for a valid IRI") {
        val actual = ResourceIri.from(validResourceIri)
        assertTrue(
          actual.isRight,
          actual.toOption.get.value == validResourceIri,
          actual.toOption.get.shortcode == Shortcode.unsafeFrom("080C"),
        )
      },
      test("should fail for an invalid ResourceIri") {
        val invalidIris = Seq(
          "http://example.com/ontology#Foo",
          "http://www.knora.org/ontology/0001/anything#Thing",
          "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
          "http://www.knora.org/ontology/0001/anything#hasListItem",
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem",
          "http://rdfh.ch/0001/thing-with-history/values/xZisRC3jPkcplt1hQQdb-A",
        )
        check(Gen.fromIterable(invalidIris)) { iri =>
          val actual = ResourceIri.from(iri)
          assertTrue(actual == Left(s"<$iri> is not a Knora resource IRI"))
        }
      },
    ),
    suite("makeNew")(
      test("should create a valid ResourceIri") {
        val shortcode   = Shortcode.unsafeFrom("0001")
        val resourceIri = ResourceIri.makeNew(shortcode)
        assertTrue(
          resourceIri.value.startsWith("http://rdfh.ch/0001/"),
          resourceIri.shortcode == shortcode,
          ResourceIri.from(resourceIri.value).isRight,
        )
      },
      test("should create unique IRIs") {
        val shortcode = Shortcode.unsafeFrom("0001")
        val iri1      = ResourceIri.makeNew(shortcode)
        val iri2      = ResourceIri.makeNew(shortcode)
        assertTrue(iri1.value != iri2.value)
      },
    ),
  )
}
