/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import zio.test.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

object ValueIriSpec extends ZIOSpecDefault {

  val spec = suite("ValueIri")(
    suite("from(String)")(
      test("should return a ValueIri for a valid value IRI") {
        val iri    = "http://rdfh.ch/0001/thing-with-history/values/xZisRC3jPkcplt1hQQdb-A"
        val result = ValueIri.from(iri)
        assertTrue(
          result.isRight,
          result.toOption.get.value == iri,
          result.toOption.get.shortcode == Shortcode.unsafeFrom("0001"),
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
          assertTrue(ValueIri.from(iri) == Left(s"<$iri> is not a Knora value IRI"))
        }
      },
    ),
    suite("from(SmartIri)")(
      test("should return a ValueIri") {
        val iri    = "http://rdfh.ch/0001/thing-with-history/values/xZisRC3jPkcplt1hQQdb-A"
        val result = ValueIri.from(iri)
        assertTrue(result.map(_.value) == Right(iri))
      },
    ),
    suite("unsafeFrom")(
      test("should return a ValueIri for a valid IRI") {
        val iri = "http://rdfh.ch/0001/thing-with-history/values/xZisRC3jPkcplt1hQQdb-A"
        assertTrue(ValueIri.unsafeFrom(iri).value == iri)
      },
      test("should throw for an invalid IRI") {
        assertTrue(
          try {
            ValueIri.unsafeFrom("invalid")
            false
          } catch { case _: IllegalArgumentException => true },
        )
      },
    ),
  )
}
