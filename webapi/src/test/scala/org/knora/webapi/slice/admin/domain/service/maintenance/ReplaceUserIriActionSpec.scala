/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service.maintenance

import zio.ZIO
import zio.test.*

import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.store.triplestore.api.TestTripleStore
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object ReplaceUserIriActionSpec extends ZIOSpecDefault {

  private val adminGraph    = AdminConstants.adminDataNamedGraph.value
  private val oldIri        = UserIri.unsafeFrom("http://rdfh.ch/users/old-user")
  private val newIri        = UserIri.unsafeFrom("http://rdfh.ch/users/new-user")
  private val projectGraph1 = "http://www.knora.org/data/0001/TestProject"
  private val projectGraph2 = "http://www.knora.org/data/0002/AnotherProject"
  private val requester     = TestDataFactory.User.rootUser

  private val baseFixture =
    s"""
       |@prefix knora-base: <http://www.knora.org/ontology/knora-base#> .
       |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
       |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
       |
       |<$adminGraph> {
       |  <${oldIri.value}> knora-admin:email "old@example.com" ;
       |                    knora-admin:givenName "Old" ;
       |                    knora-admin:familyName "User" .
       |}
       |<$projectGraph1> {
       |  <http://rdfh.ch/0001/resource1> knora-base:attachedToUser <${oldIri.value}> .
       |}
       |<$projectGraph2> {
       |  <http://rdfh.ch/0002/resource2> knora-base:deletedBy <${oldIri.value}> .
       |}
       |""".stripMargin

  private def askSubjectInAdminGraph(iri: UserIri): ZIO[TriplestoreService, Throwable, Boolean] =
    ZIO.serviceWithZIO[TriplestoreService](
      _.query(Ask(s"ASK { GRAPH <$adminGraph> { <${iri.value}> ?p ?o . } }")),
    )

  private def askObjectAnywhere(iri: UserIri): ZIO[TriplestoreService, Throwable, Boolean] =
    ZIO.serviceWithZIO[TriplestoreService](
      _.query(Ask(s"ASK { GRAPH ?g { ?s ?p <${iri.value}> . } }")),
    )

  private def service = ZIO.serviceWithZIO[ReplaceUserIriAction]

  val spec: Spec[Any, Throwable] = suite("ReplaceUserIriAction")(
    suite("execute — happy path")(
      test("removes oldIri as subject from admin graph") {
        for {
          _      <- TestTripleStore.setDatasetFromTriG(baseFixture)
          _      <- service(_.execute(oldIri, newIri, requester))
          exists <- askSubjectInAdminGraph(oldIri)
        } yield assertTrue(!exists)
      },
      test("adds newIri with all original properties to admin graph") {
        for {
          _      <- TestTripleStore.setDatasetFromTriG(baseFixture)
          _      <- service(_.execute(oldIri, newIri, requester))
          exists <- askSubjectInAdminGraph(newIri)
        } yield assertTrue(exists)
      },
      test("removes oldIri as object from all project graphs") {
        for {
          _      <- TestTripleStore.setDatasetFromTriG(baseFixture)
          _      <- service(_.execute(oldIri, newIri, requester))
          exists <- askObjectAnywhere(oldIri)
        } yield assertTrue(!exists)
      },
      test("replaces oldIri with newIri in project graph 1") {
        for {
          _      <- TestTripleStore.setDatasetFromTriG(baseFixture)
          _      <- service(_.execute(oldIri, newIri, requester))
          exists <- ZIO.serviceWithZIO[TriplestoreService](
                      _.query(
                        Ask(
                          s"""ASK { GRAPH <$projectGraph1>
                             |  { <http://rdfh.ch/0001/resource1>
                             |    <http://www.knora.org/ontology/knora-base#attachedToUser>
                             |    <${newIri.value}> . } }""".stripMargin,
                        ),
                      ),
                    )
        } yield assertTrue(exists)
      },
      test("replaces oldIri with newIri in project graph 2") {
        for {
          _      <- TestTripleStore.setDatasetFromTriG(baseFixture)
          _      <- service(_.execute(oldIri, newIri, requester))
          exists <- ZIO.serviceWithZIO[TriplestoreService](
                      _.query(
                        Ask(
                          s"""ASK { GRAPH <$projectGraph2>
                             |  { <http://rdfh.ch/0002/resource2>
                             |    <http://www.knora.org/ontology/knora-base#deletedBy>
                             |    <${newIri.value}> . } }""".stripMargin,
                        ),
                      ),
                    )
        } yield assertTrue(exists)
      },
    ),
    suite("execute — validation failures")(
      test("fails with NotFoundException when oldIri is not in admin graph") {
        val absentIri = UserIri.unsafeFrom("http://rdfh.ch/users/nonexistent")
        for {
          _   <- TestTripleStore.setDatasetFromTriG(baseFixture)
          res <- service(_.execute(absentIri, newIri, requester)).exit
        } yield assert(res)(Assertion.failsWithA[dsp.errors.NotFoundException])
      },
      test("fails with ConflictException when newIri already exists in admin graph") {
        val conflictFixture = baseFixture +
          s"""
             |<$adminGraph> {
             |  <${newIri.value}> knora-admin:email "new@example.com" .
             |}
             |""".stripMargin
        for {
          _   <- TestTripleStore.setDatasetFromTriG(conflictFixture)
          res <- service(_.execute(oldIri, newIri, requester)).exit
        } yield assert(res)(Assertion.failsWithA[dsp.errors.ConflictException])
      },
    ),
  ).provide(
    ReplaceUserIriAction.layer,
    TriplestoreServiceInMemory.emptyLayer,
    StringFormatter.test,
  )
}
