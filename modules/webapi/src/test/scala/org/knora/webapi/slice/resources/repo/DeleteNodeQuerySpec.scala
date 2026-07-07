/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.NonEmptyChunk
import zio.test.*

import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.RestrictedView

object DeleteNodeQuerySpec extends ZIOSpecDefault {

  private val testProject = KnoraProject(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
    Shortname.unsafeFrom("anything"),
    Shortcode.unsafeFrom("0001"),
    None,
    NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.from("Test project"))),
    List.empty,
    None,
    Status.Active,
    SelfJoin.CannotJoin,
    RestrictedView.default,
    Set.empty,
    Set.empty,
  )

  private val testNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/test-node")

  override def spec: Spec[TestEnvironment, Any] = suite("DeleteNodeQuerySpec")(
    suite("buildForChildNode")(
      test("should produce correct query for deleting child node") {
        val actual = DeleteNodeQuery.buildForChildNode(testNodeIri, testProject).getQueryString
        assertTrue(
          actual ==
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/lists/0001/test-node> ?p ?o .
              |?parentNode knora-base:hasSubListNode <http://rdfh.ch/lists/0001/test-node> . } }
              |WHERE { <http://rdfh.ch/lists/0001/test-node> a knora-base:ListNode ;
              |    ?p ?o .
              |?parentNode a knora-base:ListNode ;
              |    knora-base:hasSubListNode <http://rdfh.ch/lists/0001/test-node> . }""".stripMargin,
        )
      },
      test("should produce correct query for different node IRI") {
        val differentNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/another-node")
        val actual           = DeleteNodeQuery.buildForChildNode(differentNodeIri, testProject).getQueryString
        assertTrue(
          actual ==
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/lists/0001/another-node> ?p ?o .
              |?parentNode knora-base:hasSubListNode <http://rdfh.ch/lists/0001/another-node> . } }
              |WHERE { <http://rdfh.ch/lists/0001/another-node> a knora-base:ListNode ;
              |    ?p ?o .
              |?parentNode a knora-base:ListNode ;
              |    knora-base:hasSubListNode <http://rdfh.ch/lists/0001/another-node> . }""".stripMargin,
        )
      },
      test("should produce correct query for different project") {
        val otherProject = KnoraProject(
          ProjectIri.unsafeFrom("http://rdfh.ch/projects/0803"),
          Shortname.unsafeFrom("incunabula"),
          Shortcode.unsafeFrom("0803"),
          None,
          NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.from("Incunabula project"))),
          List.empty,
          None,
          Status.Active,
          SelfJoin.CannotJoin,
          RestrictedView.default,
          Set.empty,
          Set.empty,
        )
        val nodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0803/book-list-node")
        val actual  = DeleteNodeQuery.buildForChildNode(nodeIri, otherProject).getQueryString
        assertTrue(
          actual ==
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |DELETE { GRAPH <http://www.knora.org/data/0803/incunabula> { <http://rdfh.ch/lists/0803/book-list-node> ?p ?o .
              |?parentNode knora-base:hasSubListNode <http://rdfh.ch/lists/0803/book-list-node> . } }
              |WHERE { <http://rdfh.ch/lists/0803/book-list-node> a knora-base:ListNode ;
              |    ?p ?o .
              |?parentNode a knora-base:ListNode ;
              |    knora-base:hasSubListNode <http://rdfh.ch/lists/0803/book-list-node> . }""".stripMargin,
        )
      },
    ),
    suite("buildForRootNode")(
      test("should produce correct query for deleting root node") {
        val actual = DeleteNodeQuery.buildForRootNode(testNodeIri, testProject).getQueryString
        assertTrue(
          actual ==
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/lists/0001/test-node> ?p ?o . } }
              |WHERE { <http://rdfh.ch/lists/0001/test-node> a knora-base:ListNode ;
              |    ?p ?o . }""".stripMargin,
        )
      },
      test("should produce correct query for different node IRI") {
        val differentNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/root-node")
        val actual           = DeleteNodeQuery.buildForRootNode(differentNodeIri, testProject).getQueryString
        assertTrue(
          actual ==
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/lists/0001/root-node> ?p ?o . } }
              |WHERE { <http://rdfh.ch/lists/0001/root-node> a knora-base:ListNode ;
              |    ?p ?o . }""".stripMargin,
        )
      },
      test("should produce correct query for different project") {
        val otherProject = KnoraProject(
          ProjectIri.unsafeFrom("http://rdfh.ch/projects/0803"),
          Shortname.unsafeFrom("incunabula"),
          Shortcode.unsafeFrom("0803"),
          None,
          NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.from("Incunabula project"))),
          List.empty,
          None,
          Status.Active,
          SelfJoin.CannotJoin,
          RestrictedView.default,
          Set.empty,
          Set.empty,
        )
        val nodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0803/root-book-list")
        val actual  = DeleteNodeQuery.buildForRootNode(nodeIri, otherProject).getQueryString
        assertTrue(
          actual ==
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |DELETE { GRAPH <http://www.knora.org/data/0803/incunabula> { <http://rdfh.ch/lists/0803/root-book-list> ?p ?o . } }
              |WHERE { <http://rdfh.ch/lists/0803/root-book-list> a knora-base:ListNode ;
              |    ?p ?o . }""".stripMargin,
        )
      },
    ),
  )
}
