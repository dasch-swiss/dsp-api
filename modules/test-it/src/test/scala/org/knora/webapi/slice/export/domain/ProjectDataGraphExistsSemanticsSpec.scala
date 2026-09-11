/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Exercises the create-only precondition query against a live triplestore. Proves that `ProjectDataGraphExistsQuery`
 * treats a lists-only graph as absent (false) and any non-list data as present (true). Each case uses its own project
 * data graph (distinct shortname), so the cases are independent of order.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ProjectDataGraphExistsSemanticsSpec extends E2EZSpec {

  private val triplestore = ZIO.serviceWithZIO[TriplestoreService]

  private val baseProject = KnoraProject(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
    Shortname.unsafeFrom("shortname"),
    Shortcode.unsafeFrom("0001"),
    None,
    NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.from("Test project"))),
    List.empty,
    None,
    SelfJoin.CannotJoin,
    RestrictedView.default,
    Set.empty,
    Set.empty,
  )

  private def project(shortname: String): KnoraProject =
    baseProject.copy(shortname = Shortname.unsafeFrom(shortname))

  private def graphOf(shortname: String): String = s"http://www.knora.org/data/0001/$shortname"

  private val kb   = "http://www.knora.org/ontology/knora-base#"
  private val rdfs = "http://www.w3.org/2000/01/rdf-schema#"

  private def listNode(subject: String): String =
    s"""<$subject> a <${kb}ListNode> ;
       |  <${kb}listNodeName> "a list node" ;
       |  <${rdfs}label> "A list node" .""".stripMargin

  private def resource(subject: String): String =
    s"""<$subject> a <http://www.knora.org/ontology/0001/anything#Thing> ;
       |  <${rdfs}label> "A thing" .""".stripMargin

  private def seed(shortname: String, triples: String): Update =
    Update(
      s"""INSERT DATA {
         |  GRAPH <${graphOf(shortname)}> {
         |    $triples
         |  }
         |}""".stripMargin,
    )

  private def dataGraphExists(shortname: String) =
    triplestore(_.query(ProjectDataGraphExistsQuery.build(project(shortname))))

  override val e2eSpec: Spec[env, Any] = suite("ProjectDataGraphExistsQuery against a live triplestore")(
    test("returns false for an empty project data graph") {
      dataGraphExists("emptygraph").map(exists => assertTrue(!exists))
    },
    test("returns false for a graph containing only list nodes (REQ-9.2)") {
      for {
        _ <- triplestore(
               _.query(
                 seed(
                   "listsonly",
                   s"${listNode("http://rdfh.ch/lists/0001/n1")}\n${listNode("http://rdfh.ch/lists/0001/n2")}",
                 ),
               ),
             )
        exists <- dataGraphExists("listsonly")
      } yield assertTrue(!exists)
    },
    test("returns true for a graph containing a non-list resource") {
      for {
        _      <- triplestore(_.query(seed("realdata", resource("http://rdfh.ch/0001/a-thing"))))
        exists <- dataGraphExists("realdata")
      } yield assertTrue(exists)
    },
    test("returns true for a graph mixing list nodes and a resource (production shape)") {
      for {
        _ <- triplestore(
               _.query(
                 seed(
                   "mixedgraph",
                   s"${listNode("http://rdfh.ch/lists/0001/m1")}\n${resource("http://rdfh.ch/0001/m-thing")}",
                 ),
               ),
             )
        exists <- dataGraphExists("mixedgraph")
      } yield assertTrue(exists)
    },
  )
}
