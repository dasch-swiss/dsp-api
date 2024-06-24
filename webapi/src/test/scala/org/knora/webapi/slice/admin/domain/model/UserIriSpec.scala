/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import org.knora.webapi.slice.admin.domain.model.UserSpec.test
import zio.test.*

object UserIriSpec extends ZIOSpecDefault {

  private val validIris = Seq(
    "http://rdfh.ch/users/9CzAcnT0So-k10CTySHnQA",
    "http://rdfh.ch/users/A6AZpISxSm65bBHZ_G5Z4w",
    "http://rdfh.ch/users/AnythingAdminUser",
    "http://rdfh.ch/users/AnythingAdminUser",
    "http://rdfh.ch/users/PSGbemdjZi4kQ6GHJVkLGE",
    "http://rdfh.ch/users/_fH9FS-VRMiPPiIMRpjevA",
    "http://rdfh.ch/users/activites-cs-test-user1",
    "http://rdfh.ch/users/drawings-gods-test-ddd1",
    "http://rdfh.ch/users/drawings-gods-test-user-metaannotator",
    "http://rdfh.ch/users/images-reviewer-user",
    "http://rdfh.ch/users/jDEEitJESRi3pDaDjjQ1WQ",
    "http://rdfh.ch/users/mls-0807-import-user",
    "http://rdfh.ch/users/multiuser",
    "http://rdfh.ch/users/normaluser",
    "http://rdfh.ch/users/parole-religieuse-test-user1",
    "http://rdfh.ch/users/reforme-geneve-test1",
    "http://rdfh.ch/users/root",
    "http://rdfh.ch/users/root",
    "http://rdfh.ch/users/roud-oeuvres-test-user1",
    "http://rdfh.ch/users/stardom-archivist-test-user",
    "http://rdfh.ch/users/stardom-cacourcoux",
    "http://rdfh.ch/users/stardom-import-snf",
    "http://rdfh.ch/users/subotic",
    "http://rdfh.ch/users/superuser",
    "http://rdfh.ch/users/theatre-societe-test-user",
    "http://www.knora.org/ontology/knora-admin#AnonymousUser",
    "http://www.knora.org/ontology/knora-admin#SystemUser",
  )

  val spec = suite("UserIri")(
    test("must not be empty") {
      check(Gen.fromIterable(validIris))(iri => assertTrue(UserIri.from(iri).map(_.value) == Right(iri)))
    },
    test("make new should create a valid user iri") {
      assertTrue(UserIri.makeNew.value.startsWith("http://rdfh.ch/users/"))
    },
    test("built in users should be builtIn") {
      val builtInIris = Gen.fromIterable(
        Seq(
          "http://www.knora.org/ontology/knora-admin#AnonymousUser",
          "http://www.knora.org/ontology/knora-admin#SystemUser",
          "http://www.knora.org/ontology/knora-admin#AnonymousUser",
        ),
      )
      check(builtInIris) { i =>
        val userIri = UserIri.unsafeFrom(i)
        assertTrue(!userIri.isRegularUser, userIri.isBuiltInUser)
      }
    },
    test("regular user iris should not be builtIn") {
      val builtInIris = Gen.fromIterable(
        Seq(
          "http://rdfh.ch/users/jDEEitJESRi3pDaDjjQ1WQ",
          "http://rdfh.ch/users/PSGbemdjZi4kQ6GHJVkLGE",
        ),
      )
      check(builtInIris) { i =>
        val userIri = UserIri.unsafeFrom(i)
        assertTrue(userIri.isRegularUser, !userIri.isBuiltInUser)
      }
    },
    test("pass an empty value and return an error") {
      assertTrue(UserIri.from("") == Left("User IRI cannot be empty."))
    },
    test("pass an invalid value and return an error") {
      val invalidIris = Gen.fromIterable(
        Seq(
          "Invalid IRI",
          "http://rdfh.ch/user/AnythingAdminUser",
          "http://rdfh.ch/users/AnythingAdminUser/",
        ),
      )
      check(invalidIris)(i => assertTrue(UserIri.from(i) == Left(s"User IRI is invalid.")))
    },
  )
}
