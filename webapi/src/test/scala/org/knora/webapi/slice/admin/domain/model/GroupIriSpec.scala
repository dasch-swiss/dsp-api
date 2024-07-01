/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import org.knora.webapi.messages.OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion
import zio.Chunk
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
    test("allow prefixed builtin GroupIris") {
      val builtIn = Chunk("UnknownUser", "KnownUser", "Creator", "ProjectMember", "ProjectAdmin", "SystemAdmin")
        .map("knora-admin:" + _)
      check(Gen.fromIterable(builtIn)) { it =>
        assertTrue(GroupIri.from(it).map(_.value) == Right(it.replace("knora-admin:", KnoraAdminPrefixExpansion)))
      }
    },
    test("be created from a valid value") {
      val validIris = Gen.fromIterable(
        Seq(
          "http://rdfh.ch/groups/0001/40-characters-iri-for-testing-purposes-1",
          "http://rdfh.ch/groups/ABCD/jDEEitJESRi3pDaDjjQ1WQ",
          "http://rdfh.ch/groups/0111/UUID1",
          "http://rdfh.ch/groups/0111/1234",
        ),
      )
      check(validIris)(i => assertTrue(GroupIri.from(i).isRight))
    },
    test("not be created from an invalid value") {
      val invalidIris = Gen.fromIterable(
        Seq(
          "Invalid IRI",
          "http://rdfh.ch/groups/0111/123",
          "http://rdfh.ch/groups/0001/41-characters-iri-for-testing-purposes-12",
          "http://rdfh.ch/groups/EFGH/jDEEitJESRi3pDaDjjQ1WQ",
          "http://rdfh.ch/groups/jDEEitJESRi3pDaDjjQ1WQ",
        ),
      )
      check(invalidIris)(i => assertTrue(GroupIri.from(i) == Left(s"Group IRI is invalid: $i")))
    },
  )
}
