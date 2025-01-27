/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.model
import zio.test.*

object OntologyNameSpec extends ZIOSpecDefault {
  val spec = suite("OntologyName")(
    test("should create a valid OntologyName") {
      val validNames = List("anything", "beol", "limc")
      check(Gen.fromIterable(validNames)) { name =>
        val result = OntologyName.from(name)
        assertTrue(result.map(_.value) == Right(name))
      }
    },
    test("must not contain reserved words") {
      val reservedWords = List("knora", "ontology", "rdf", "rdfs", "owl", "xsd", "schema", "shared", "simple")
      check(Gen.fromIterable(reservedWords)) { reservedWord =>
        val result = OntologyName.from(reservedWord)
        assertTrue(
          result == Left(
            "OntologyName must not contain reserved words: HashSet(rdf, knora, standoff, ontology, knora-admin, " +
              "knora-base, owl, salsah-gui, simple, xsd, shared, knora-api, schema, rdfs).",
          ),
        )
      }
    },
  )

}
