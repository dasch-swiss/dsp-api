/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.model
import zio.test.*

object OntologyNameSpec extends ZIOSpecDefault {
  val spec = suite("OntologyName")(
    test("should create a valid OntologyName") {
      val validNames = List("anything", "beol", "limc", "test", "AVeryVeryVeryVeryLongLongName", "another-onto")
      check(Gen.fromIterable(validNames)) { name =>
        val result = OntologyName.from(name)
        assertTrue(result.map(_.value) == Right(name))
      }
    },
    test("should create internal OntologyName") {
      val internalNames = List("knora-api", "knora-admin", "knora-base", "salsah-gui")
      check(Gen.fromIterable(internalNames)) { name =>
        val result = OntologyName.from(name)
        assertTrue(result.map(it => (it.isInternal, it.value)) == Right((true, name)))
      }
    },
    test("must not contain reserved words") {
      val reservedWords = List("owl", "ontology", "rdf", "rdfs", "owl", "xsd", "schema", "shared", "simple")
      check(Gen.fromIterable(reservedWords)) { reservedWord =>
        val result = OntologyName.from(reservedWord)
        assertTrue(
          result == Left(
            "OntologyName must not contain reserved words: rdf, shared, ontology, owl, simple, xsd, schema, rdfs.",
          ),
        )
      }
    },
    test("must not create with invalid value") {
      val invalidNames = List("1", "1abc", "abc.1", "abc/1", "abc 1", "abc@1", "abc#1", "abc$1", "abc%1", "some-knora")
      check(Gen.fromIterable(invalidNames)) { invalidName =>
        assertTrue(OntologyName.from(invalidName).isLeft)
      }
    },
  )
}
