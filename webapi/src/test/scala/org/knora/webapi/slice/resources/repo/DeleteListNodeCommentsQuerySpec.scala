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

object DeleteListNodeCommentsQuerySpec extends ZIOSpecDefault {

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

  override def spec: Spec[TestEnvironment, Any] = suite("DeleteListNodeCommentsQuerySpec")(
    test("should produce correct query for deleting list node comments") {
      val actual = DeleteListNodeCommentsQuery.build(testNodeIri, testProject).getQueryString
      assertTrue(
        actual ==
          """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/lists/0001/test-node> rdfs:comment ?comments . } }
            |WHERE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/lists/0001/test-node> rdfs:comment ?comments . } }""".stripMargin,
      )
    },
    test("should produce correct query for different node IRI") {
      val differentNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/another-node")
      val actual           = DeleteListNodeCommentsQuery.build(differentNodeIri, testProject).getQueryString
      assertTrue(
        actual ==
          """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/lists/0001/another-node> rdfs:comment ?comments . } }
            |WHERE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/lists/0001/another-node> rdfs:comment ?comments . } }""".stripMargin,
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
      val actual  = DeleteListNodeCommentsQuery.build(nodeIri, otherProject).getQueryString
      assertTrue(
        actual ==
          """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |DELETE { GRAPH <http://www.knora.org/data/0803/incunabula> { <http://rdfh.ch/lists/0803/book-list-node> rdfs:comment ?comments . } }
            |WHERE { GRAPH <http://www.knora.org/data/0803/incunabula> { <http://rdfh.ch/lists/0803/book-list-node> rdfs:comment ?comments . } }""".stripMargin,
      )
    },
  )
}
