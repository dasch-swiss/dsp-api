/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.model
import zio.test.*

object OntologyNameSpec extends ZIOSpecDefault {
  val spec = suite("OntologyNameSpec")(test("should") {
    val validNames = List("anything")
    check(Gen.fromIterable(validNames)) { name =>
      val result = OntologyName.from(name)
      assertTrue(result.map(_.value) == Right(name))
    }
  })
}
