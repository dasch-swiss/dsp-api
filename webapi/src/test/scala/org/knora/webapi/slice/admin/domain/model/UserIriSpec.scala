/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.*

object UserIriSpec extends ZIOSpecDefault {

  private val testIris = Seq(
    "http://rdfh.ch/users/9CzAcnT0So-k10CTySHnQA",
    "http://rdfh.ch/users/A6AZpISxSm65bBHZ_G5Z4w",
    "http://rdfh.ch/users/AnythingAdminUser",
    "http://rdfh.ch/users/activites-cs-test-user1",
    "http://rdfh.ch/users/drawings-gods-test-ddd1",
    "http://rdfh.ch/users/drawings-gods-test-user-metaannotator",
    "http://rdfh.ch/users/multiuser",
    "http://rdfh.ch/users/normaluser",
    "http://rdfh.ch/users/parole-religieuse-test-user1",
    "http://rdfh.ch/users/reforme-geneve-test1",
    "http://rdfh.ch/users/root",
    "http://rdfh.ch/users/roud-oeuvres-test-user1",
    "http://rdfh.ch/users/stardom-archivist-test-user",
    "http://rdfh.ch/users/stardom-cacourcoux",
    "http://rdfh.ch/users/stardom-import-snf",
    "http://rdfh.ch/users/superuser",
    "http://rdfh.ch/users/theatre-societe-test-user",
    "http://www.knora.org/ontology/knora-admin#AnonymousUser",
    "http://www.knora.org/ontology/knora-admin#SystemUser",
  )

  val spec = suite("UserIriSpec")(test("") {
    check(Gen.fromIterable(testIris))(iri => assertTrue(UserIri.from(iri).map(_.value) == Right(iri)))
  })
}
