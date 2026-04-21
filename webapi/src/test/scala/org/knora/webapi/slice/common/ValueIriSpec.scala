/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import zio.test.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

object ValueIriSpec extends ZIOSpecDefault {

  private val validValueIri = "http://rdfh.ch/0001/thing-with-history/values/xZisRC3jPkcplt1hQQdb-A"

  val spec = suite("ValueIri")(
    suite("from")(
      test("should return a ValueIri for a valid IRI") {
        val actual = ValueIri.from(validValueIri)
        assertTrue(
          actual.isRight,
          actual.toOption.get.value == validValueIri,
          actual.toOption.get.shortcode == Shortcode.unsafeFrom("0001"),
          actual.toOption.get.resourceId == ResourceId.unsafeFrom("thing-with-history"),
          actual.toOption.get.valueId == ValueId.unsafeFrom("xZisRC3jPkcplt1hQQdb-A"),
        )
      },
      test("should fail for an invalid ValueIri") {
        val invalidIris = Seq(
          "http://example.com/ontology#Foo",
          "http://www.knora.org/ontology/0001/anything#Thing",
          "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
          "http://www.knora.org/ontology/0001/anything#hasListItem",
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem",
          "http://rdfh.ch/080C/Ef9heHjPWDS7dMR_gGax2Q",
        )
        check(Gen.fromIterable(invalidIris)) { iri =>
          val actual = ValueIri.from(iri)
          assertTrue(actual == Left(s"<$iri> is not a Knora value IRI"))
        }
      },
    ),
    suite("makeNew")(
      test("should create a valid ValueIri") {
        val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing1")
        val valueIri    = ValueIri.makeNew(resourceIri)
        assertTrue(
          valueIri.value.startsWith("http://rdfh.ch/0001/thing1/values/"),
          valueIri.shortcode == Shortcode.unsafeFrom("0001"),
          valueIri.resourceId == ResourceId.unsafeFrom("thing1"),
          ValueIri.from(valueIri.value).isRight,
        )
      },
      test("should create unique IRIs") {
        val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing1")
        val iri1        = ValueIri.makeNew(resourceIri)
        val iri2        = ValueIri.makeNew(resourceIri)
        assertTrue(iri1.value != iri2.value)
      },
    ),
    suite("sameResourceAs")(
      test("should return true for values on the same resource") {
        val iri1 = ValueIri.unsafeFrom("http://rdfh.ch/0001/thing1/values/val1")
        val iri2 = ValueIri.unsafeFrom("http://rdfh.ch/0001/thing1/values/val2")
        assertTrue(iri1.sameResourceAs(iri2))
      },
      test("should return false for values on different resources") {
        val iri1 = ValueIri.unsafeFrom("http://rdfh.ch/0001/thing1/values/val1")
        val iri2 = ValueIri.unsafeFrom("http://rdfh.ch/0001/thing2/values/val1")
        assertTrue(!iri1.sameResourceAs(iri2))
      },
    ),
  )
}
