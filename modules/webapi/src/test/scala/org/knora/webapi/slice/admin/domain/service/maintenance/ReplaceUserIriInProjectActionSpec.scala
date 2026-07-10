/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service.maintenance

import org.junit.runner.RunWith
import zio.ZIO
import zio.test.*

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoInMemory
import org.knora.webapi.store.triplestore.api.TestTripleStore
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

@RunWith(classOf[DspZTestJUnitRunner])
class ReplaceUserIriInProjectActionSpec extends ZIOSpecDefault {

  private val adminGraph    = AdminConstants.adminDataNamedGraph.value
  private val testProject   = TestDataFactory.someProject
  private val projectAGraph =
    ProjectService.projectDataNamedGraphV2(testProject).value
  private val projectBGraph = "http://www.knora.org/data/0002/OtherProject"

  private val oldIri    = UserIri.unsafeFrom("http://rdfh.ch/users/old-user-in-project")
  private val newIri    = UserIri.unsafeFrom("http://rdfh.ch/users/new-user-in-project")
  private val requester = TestDataFactory.User.rootUser

  private val baseFixture =
    s"""
       |@prefix knora-base:  <http://www.knora.org/ontology/knora-base#> .
       |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
       |
       |<$adminGraph> {
       |  <${oldIri.value}> knora-admin:email "old@example.com" .
       |  <${newIri.value}> knora-admin:email "new@example.com" ;
       |                    knora-admin:isInProject <${testProject.id.value}> .
       |}
       |<$projectAGraph> {
       |  <http://rdfh.ch/resource1> knora-base:attachedToUser <${oldIri.value}> .
       |  <http://rdfh.ch/resource2> knora-base:deletedBy     <${oldIri.value}> .
       |}
       |<$projectBGraph> {
       |  <http://rdfh.ch/resource3> knora-base:attachedToUser <${oldIri.value}> .
       |}
       |""".stripMargin

  private def createProject = ZIO.serviceWithZIO[KnoraProjectRepoInMemory](_.save(testProject))

  private def askObjectInGraph(iri: UserIri, graph: String): ZIO[TriplestoreService, Throwable, Boolean] =
    ZIO.serviceWithZIO[TriplestoreService](
      _.query(Ask(s"ASK { GRAPH <$graph> { ?s ?p <${iri.value}> . } }")),
    )

  private def askSubjectInAdminGraph(iri: UserIri): ZIO[TriplestoreService, Throwable, Boolean] =
    ZIO.serviceWithZIO[TriplestoreService](
      _.query(Ask(s"ASK { GRAPH <$adminGraph> { <${iri.value}> ?p ?o . } }")),
    )

  private def service = ZIO.serviceWithZIO[ReplaceUserIriInProjectAction]

  val spec: Spec[Any, Throwable] = suite("ReplaceUserIriInProjectAction")(
    suite("execute - happy path")(
      test("replaces oldIri with newIri in the target project graph") {
        for {
          _      <- createProject
          _      <- TestTripleStore.setDatasetFromTriG(baseFixture)
          _      <- service(_.execute(testProject.shortcode, oldIri, newIri, requester))
          exists <- askObjectInGraph(newIri, projectAGraph)
        } yield assertTrue(exists)
      },
      test("removes all object-position references to oldIri from the target project graph") {
        for {
          _      <- createProject
          _      <- TestTripleStore.setDatasetFromTriG(baseFixture)
          _      <- service(_.execute(testProject.shortcode, oldIri, newIri, requester))
          exists <- askObjectInGraph(oldIri, projectAGraph)
        } yield assertTrue(!exists)
      },
      test("does not touch the admin graph") {
        for {
          _         <- createProject
          _         <- TestTripleStore.setDatasetFromTriG(baseFixture)
          _         <- service(_.execute(testProject.shortcode, oldIri, newIri, requester))
          oldExists <- askSubjectInAdminGraph(oldIri)
          newExists <- askSubjectInAdminGraph(newIri)
        } yield assertTrue(oldExists, newExists)
      },
      test("does not touch other project graphs") {
        for {
          _      <- createProject
          _      <- TestTripleStore.setDatasetFromTriG(baseFixture)
          _      <- service(_.execute(testProject.shortcode, oldIri, newIri, requester))
          exists <- askObjectInGraph(oldIri, projectBGraph)
        } yield assertTrue(exists)
      },
    ),
    suite("execute - built-in user as oldIri")(
      test("replaces oldIri with newIri when oldIri is a built-in user IRI") {
        val systemUserIri = UserIri.unsafeFrom("http://www.knora.org/ontology/knora-admin#SystemUser")
        val fixture       =
          s"""
             |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
             |@prefix knora-base:  <http://www.knora.org/ontology/knora-base#> .
             |<$adminGraph> {
             |  <${newIri.value}> knora-admin:email "new@example.com" ;
             |                    knora-admin:isInProject <${testProject.id.value}> .
             |}
             |<$projectAGraph> {
             |  <http://rdfh.ch/resource1> knora-base:attachedToUser <${systemUserIri.value}> .
             |}
             |""".stripMargin
        for {
          _      <- createProject
          _      <- TestTripleStore.setDatasetFromTriG(fixture)
          _      <- service(_.execute(testProject.shortcode, systemUserIri, newIri, requester))
          exists <- askObjectInGraph(newIri, projectAGraph)
        } yield assertTrue(exists)
      },
    ),
    suite("execute - validation failures")(
      test("fails with NotFoundException when project is not found") {
        val unknownShortcode = Shortcode.unsafeFrom("FFFF")
        for {
          _   <- TestTripleStore.setDatasetFromTriG(baseFixture)
          res <- service(_.execute(unknownShortcode, oldIri, newIri, requester)).exit
        } yield assert(res)(Assertion.failsWithA[NotFoundException])
      },
      test("fails with NotFoundException when oldIri is not in admin graph") {
        val absentIri = UserIri.unsafeFrom("http://rdfh.ch/users/absent")
        for {
          _   <- createProject
          _   <- TestTripleStore.setDatasetFromTriG(baseFixture)
          res <- service(_.execute(testProject.shortcode, absentIri, newIri, requester)).exit
        } yield assert(res)(Assertion.failsWithA[NotFoundException])
      },
      test("fails with NotFoundException when newIri is not in admin graph") {
        val absentIri = UserIri.unsafeFrom("http://rdfh.ch/users/absent-new")
        for {
          _   <- createProject
          _   <- TestTripleStore.setDatasetFromTriG(baseFixture)
          res <- service(_.execute(testProject.shortcode, oldIri, absentIri, requester)).exit
        } yield assert(res)(Assertion.failsWithA[NotFoundException])
      },
      test("fails with BadRequestException when newIri is not a member of the project") {
        val nonMemberIri         = UserIri.unsafeFrom("http://rdfh.ch/users/non-member")
        val fixtureWithNonMember =
          s"""
             |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
             |@prefix knora-base:  <http://www.knora.org/ontology/knora-base#> .
             |<$adminGraph> {
             |  <${oldIri.value}>      knora-admin:email "old@example.com" .
             |  <${nonMemberIri.value}> knora-admin:email "nonmember@example.com" .
             |}
             |<$projectAGraph> {
             |  <http://rdfh.ch/r1> knora-base:attachedToUser <${oldIri.value}> .
             |}
             |""".stripMargin
        for {
          _   <- createProject
          _   <- TestTripleStore.setDatasetFromTriG(fixtureWithNonMember)
          res <- service(_.execute(testProject.shortcode, oldIri, nonMemberIri, requester)).exit
        } yield assert(res)(Assertion.failsWithA[BadRequestException])
      },
      test("fails with NotFoundException when oldIri has no references in the project data graph") {
        val fixtureNoRefs =
          s"""
             |@prefix knora-admin: <http://www.knora.org/ontology/knora-admin#> .
             |<$adminGraph> {
             |  <${oldIri.value}> knora-admin:email "old@example.com" .
             |  <${newIri.value}> knora-admin:email "new@example.com" ;
             |                    knora-admin:isInProject <${testProject.id.value}> .
             |}
             |""".stripMargin
        for {
          _   <- createProject
          _   <- TestTripleStore.setDatasetFromTriG(fixtureNoRefs)
          res <- service(_.execute(testProject.shortcode, oldIri, newIri, requester)).exit
        } yield assert(res)(Assertion.failsWithA[NotFoundException])
      },
    ),
  ).provide(
    IriConverter.layer,
    KnoraProjectRepoInMemory.layer,
    KnoraProjectService.layer,
    LicenseRepo.layer,
    OntologyRepoInMemory.emptyLayer,
    ReplaceUserIriInProjectAction.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.emptyLayer,
  )
}
