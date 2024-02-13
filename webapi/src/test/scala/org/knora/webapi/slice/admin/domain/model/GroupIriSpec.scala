/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.Gen
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.check

object GroupIriSpec extends ZIOSpecDefault {
  override val spec: Spec[Any, Nothing] = suite("GroupIri should")(
    test("not be created from an empty value") {
      assertTrue(GroupIri.from("") == Left("Group IRI cannot be empty."))
    },
    test("be created from valid value") {
      val validIris = Gen.fromIterable(
        Seq(
          "http://rdfh.ch/groups/0001/30-characters-iri-for-testing1",
          "http://rdfh.ch/groups/ABCD/jDEEitJESRi3pDaDjjQ1WQ",
          "http://rdfh.ch/groups/0111/UUID1",
          "http://rdfh.ch/groups/0111/1234"
        )
      )
      check(validIris)(i => assertTrue(GroupIri.from(i).isRight))
    },
    test("not be created from an invalid value") {
      val invalidIris = Gen.fromIterable(
        Seq(
          "Invalid IRI",
          "http://rdfh.ch/groups/0111/123",
          "http://rdfh.ch/groups/0001/31-characters-iri-for-testing12",
          "http://rdfh.ch/groups/EFGH/jDEEitJESRi3pDaDjjQ1WQ",
          "http://rdfh.ch/groups/jDEEitJESRi3pDaDjjQ1WQ"
        )
      )
      check(invalidIris)(i => assertTrue(GroupIri.from(i) == Left(s"Group IRI is invalid.")))
    }
  )
}
