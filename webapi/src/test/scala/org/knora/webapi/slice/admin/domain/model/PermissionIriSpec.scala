/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.{Gen, Spec, ZIOSpecDefault, assertTrue, check}

object PermissionIriSpec extends ZIOSpecDefault {
  override val spec: Spec[Any, Nothing] = suite("PermissionIri should")(
    test("not be created from an empty value") {
      assertTrue(PermissionIri.from("") == Left("Permission IRI cannot be empty."))
    },
    test("be created from a valid value") {
      val validIris = Gen.fromIterable(
        Seq(
          "http://rdfh.ch/permissions/0001/30-characters-iri-for-testing1",
          "http://rdfh.ch/permissions/ABCD/jDEEitJESRi3pDaDjjQ1WQ",
          "http://rdfh.ch/permissions/0111/U_U_I_D_1",
          "http://rdfh.ch/permissions/0111/12"
        )
      )
      check(validIris)(i => assertTrue(PermissionIri.from(i).isRight))
    },
    test("not be created from an invalid value") {
      val invalidIris = Gen.fromIterable(
        Seq(
          "Invalid IRI",
          "http://rdfh.ch/permissions/0111/1",
          "http://rdfh.ch/permissions/0001/31-characters-iri-for-testing12",
          "http://rdfh.ch/permissions/EFGH/jDEEitJESRi3pDaDjjQ1WQ",
          "http://rdfh.ch/permissions/jDEEitJESRi3pDaDjjQ1WQ"
        )
      )
      check(invalidIris)(i => assertTrue(PermissionIri.from(i) == Left(s"Permission IRI is invalid.")))
    }
  )
}
