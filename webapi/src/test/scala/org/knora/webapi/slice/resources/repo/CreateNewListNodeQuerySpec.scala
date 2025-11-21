/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.NonEmptyChunk
import zio.Scope
import zio.test.*

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.Comments
import org.knora.webapi.slice.admin.domain.model.ListProperties.Labels
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListName
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.common.domain.LanguageCode

object CreateNewListNodeQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

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

  private val testListIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/testList")
  private val testListName = ListName.unsafeFrom("TestList")
  private val testLabels = Labels.unsafeFrom(
    Seq(
      StringLiteralV2.from("Test List", LanguageCode.EN),
      StringLiteralV2.from("Liste de test", LanguageCode.FR),
    ),
  )
  private val testComments = Comments.unsafeFrom(
    Seq(
      StringLiteralV2.from("A test list", LanguageCode.EN),
      StringLiteralV2.from("Une liste de test", LanguageCode.FR),
    ),
  )

  private val testParentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/parentList")
  private val testRootIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/rootList")

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("CreateNewListNodeQuerySpec")(
    suite("forRootNode")(
      test("should produce the correct query for a root node with name, labels, and comments") {
        val query = CreateNewListNodeQuery.forRootNode(
          project = testProject,
          node = testListIri,
          name = Some(testListName),
          labels = testLabels,
          comments = testComments,
        )

        assertTrue(
          query.getQueryString() == """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                      |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                      |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                      |INSERT { <http://rdfh.ch/lists/0001/testList> a knora-base:ListNode .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:listNodeName "TestList" .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:attachedToProject <http://rdfh.ch/projects/0001> .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:isRootNode true .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:label "Test List"@en .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:label "Liste de test"@fr .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:comment "A test list"@en .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:comment "Une liste de test"@fr . }
                                      |WHERE {}""".stripMargin,
        )
      },
      test("should produce the correct query for a root node without a name") {
        val query = CreateNewListNodeQuery.forRootNode(
          project = testProject,
          node = testListIri,
          name = None,
          labels = testLabels,
          comments = testComments,
        )

        assertTrue(
          query.getQueryString() == """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                      |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                      |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                      |INSERT { <http://rdfh.ch/lists/0001/testList> a knora-base:ListNode .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:attachedToProject <http://rdfh.ch/projects/0001> .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:isRootNode true .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:label "Test List"@en .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:label "Liste de test"@fr .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:comment "A test list"@en .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:comment "Une liste de test"@fr . }
                                      |WHERE {}""".stripMargin,
        )
      },
      test("should produce the correct query for a root node with plain string labels") {
        val plainLabels   = Labels.unsafeFrom(Seq(StringLiteralV2.from("Plain Label")))
        val plainComments = Comments.unsafeFrom(Seq(StringLiteralV2.from("Plain Comment")))
        val query = CreateNewListNodeQuery.forRootNode(
          project = testProject,
          node = testListIri,
          name = Some(testListName),
          labels = plainLabels,
          comments = plainComments,
        )

        assertTrue(
          query.getQueryString() == """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                      |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                      |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                      |INSERT { <http://rdfh.ch/lists/0001/testList> a knora-base:ListNode .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:listNodeName "TestList" .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:attachedToProject <http://rdfh.ch/projects/0001> .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:isRootNode true .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:label "Plain Label" .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:comment "Plain Comment" . }
                                      |WHERE {}""".stripMargin,
        )
      },
    ),
    suite("forSubNode")(
      test("should produce the correct query for a sub node with all parameters") {
        val query = CreateNewListNodeQuery.forSubNode(
          project = testProject,
          node = testListIri,
          name = Some(testListName),
          labels = testLabels,
          comments = Some(testComments),
          parent = testParentIri,
          root = testRootIri,
          position = 5,
        )

        assertTrue(
          query.getQueryString() == """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                      |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                      |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                      |INSERT { <http://rdfh.ch/lists/0001/testList> a knora-base:ListNode .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:listNodeName "TestList" .
                                      |<http://rdfh.ch/lists/0001/parentList> knora-base:hasSublistNode <http://rdfh.ch/lists/0001/testList> .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:hasRootNode <http://rdfh.ch/lists/0001/rootList> .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:listNodePosition 5 .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:label "Test List"@en .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:label "Liste de test"@fr .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:comment "A test list"@en .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:comment "Une liste de test"@fr . }
                                      |WHERE {}""".stripMargin,
        )
      },
      test("should produce the correct query for a sub node without a name") {
        val query = CreateNewListNodeQuery.forSubNode(
          project = testProject,
          node = testListIri,
          name = None,
          labels = testLabels,
          comments = Some(testComments),
          parent = testParentIri,
          root = testRootIri,
          position = 0,
        )

        assertTrue(
          query.getQueryString() == """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                      |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                      |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                      |INSERT { <http://rdfh.ch/lists/0001/testList> a knora-base:ListNode .
                                      |<http://rdfh.ch/lists/0001/parentList> knora-base:hasSublistNode <http://rdfh.ch/lists/0001/testList> .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:hasRootNode <http://rdfh.ch/lists/0001/rootList> .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:listNodePosition 0 .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:label "Test List"@en .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:label "Liste de test"@fr .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:comment "A test list"@en .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:comment "Une liste de test"@fr . }
                                      |WHERE {}""".stripMargin,
        )
      },
      test("should produce the correct query for a sub node without comments") {
        val query = CreateNewListNodeQuery.forSubNode(
          project = testProject,
          node = testListIri,
          name = Some(testListName),
          labels = testLabels,
          comments = None,
          parent = testParentIri,
          root = testRootIri,
          position = 1,
        )

        assertTrue(
          query.getQueryString() == """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                      |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                      |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                      |INSERT { <http://rdfh.ch/lists/0001/testList> a knora-base:ListNode .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:listNodeName "TestList" .
                                      |<http://rdfh.ch/lists/0001/parentList> knora-base:hasSublistNode <http://rdfh.ch/lists/0001/testList> .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:hasRootNode <http://rdfh.ch/lists/0001/rootList> .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:listNodePosition 1 .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:label "Test List"@en .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:label "Liste de test"@fr . }
                                      |WHERE {}""".stripMargin,
        )
      },
      test("should produce the correct query for a sub node with position -1") {
        val query = CreateNewListNodeQuery.forSubNode(
          project = testProject,
          node = testListIri,
          name = Some(testListName),
          labels = testLabels,
          comments = Some(testComments),
          parent = testParentIri,
          root = testRootIri,
          position = -1,
        )

        assertTrue(
          query.getQueryString() == """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                      |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                      |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                      |INSERT { <http://rdfh.ch/lists/0001/testList> a knora-base:ListNode .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:listNodeName "TestList" .
                                      |<http://rdfh.ch/lists/0001/parentList> knora-base:hasSublistNode <http://rdfh.ch/lists/0001/testList> .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:hasRootNode <http://rdfh.ch/lists/0001/rootList> .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:listNodePosition -1 .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:label "Test List"@en .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:label "Liste de test"@fr .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:comment "A test list"@en .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:comment "Une liste de test"@fr . }
                                      |WHERE {}""".stripMargin,
        )
      },
      test("should produce the correct query for a sub node with plain string labels and no name or comments") {
        val plainLabels = Labels.unsafeFrom(Seq(StringLiteralV2.from("Child Node")))
        val query = CreateNewListNodeQuery.forSubNode(
          project = testProject,
          node = testListIri,
          name = None,
          labels = plainLabels,
          comments = None,
          parent = testParentIri,
          root = testRootIri,
          position = 10,
        )

        assertTrue(
          query.getQueryString() == """PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                      |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                      |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                      |INSERT { <http://rdfh.ch/lists/0001/testList> a knora-base:ListNode .
                                      |<http://rdfh.ch/lists/0001/parentList> knora-base:hasSublistNode <http://rdfh.ch/lists/0001/testList> .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:hasRootNode <http://rdfh.ch/lists/0001/rootList> .
                                      |<http://rdfh.ch/lists/0001/testList> knora-base:listNodePosition 10 .
                                      |<http://rdfh.ch/lists/0001/testList> rdfs:label "Child Node" . }
                                      |WHERE {}""".stripMargin,
        )
      },
    ),
  )
}
