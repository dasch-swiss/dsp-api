/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.*
import zio.NonEmptyChunk
import zio.test.*

import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.Comments
import org.knora.webapi.slice.admin.domain.model.ListProperties.Labels
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListName
import org.knora.webapi.slice.admin.domain.model.ListProperties.Position
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.common.domain.LanguageCode.DE
import org.knora.webapi.slice.common.domain.LanguageCode.EN
import org.knora.webapi.slice.common.domain.LanguageCode.FR
import org.knora.webapi.slice.common.domain.LanguageCode.IT

object CreateListNodeQuerySpec extends ZIOSpecDefault {

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

  private val rootNodeIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/root-node")
  private val childNodeIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/child-node")
  private val parentNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/parent-node")

  override def spec: Spec[TestEnvironment, Any] = suite("CreateListNodeQuerySpec")(
    test("should produce correct query for root node with name, labels, and comments") {
      val listName = ListName.unsafeFrom("testList")
      val labels   = Labels.unsafeFrom(
        List(
          StringLiteralV2.from("Test List", EN),
          StringLiteralV2.from("Liste de test", FR),
        ),
      )
      val comments = Comments.unsafeFrom(
        List(
          StringLiteralV2.from("This is a test list", EN),
          StringLiteralV2.from("Ceci est une liste de test", FR),
        ),
      )

      val query = CreateListNodeQuery.build(
        knoraProject = testProject,
        node = rootNodeIri,
        parent = None,
        name = Some(listName),
        labels = labels,
        comments = comments,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/lists/0001/root-node> a knora-base:ListNode .
            |<http://rdfh.ch/lists/0001/root-node> knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
            |    knora-base:isRootNode true .
            |<http://rdfh.ch/lists/0001/root-node> knora-base:listNodeName "testList" .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:label "Test List"@en .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:label "Liste de test"@fr .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:comment "This is a test list"@en .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:comment "Ceci est une liste de test"@fr . } }
            |WHERE {}""".stripMargin,
      )
    },
    test("should produce correct query for root node with labels and comments") {
      val labels = Labels.unsafeFrom(
        List(
          StringLiteralV2.from("Simple List", EN),
        ),
      )
      val comments = Comments.unsafeFrom(
        List(
          StringLiteralV2.from("A simple list", EN),
        ),
      )

      val query = CreateListNodeQuery.build(
        knoraProject = testProject,
        node = rootNodeIri,
        parent = None,
        name = None,
        labels = labels,
        comments = comments,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/lists/0001/root-node> a knora-base:ListNode .
            |<http://rdfh.ch/lists/0001/root-node> knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
            |    knora-base:isRootNode true .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:label "Simple List"@en .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:comment "A simple list"@en . } }
            |WHERE {}""".stripMargin,
      )
    },
    test("should produce correct query for child node with parent, position, name, labels, and comments") {
      val listName = ListName.unsafeFrom("childNode")
      val labels   = Labels.unsafeFrom(
        List(
          StringLiteralV2.from("Child Node", EN),
        ),
      )
      val comments = Comments.unsafeFrom(
        List(
          StringLiteralV2.from("A child node", EN),
        ),
      )
      val position = Position.unsafeFrom(0)

      val query = CreateListNodeQuery.build(
        knoraProject = testProject,
        node = childNodeIri,
        parent = Some((parentNodeIri, rootNodeIri, position)),
        name = Some(listName),
        labels = labels,
        comments = comments,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/lists/0001/child-node> a knora-base:ListNode .
            |<http://rdfh.ch/lists/0001/parent-node> knora-base:hasSubListNode <http://rdfh.ch/lists/0001/child-node> .
            |<http://rdfh.ch/lists/0001/child-node> knora-base:hasRootNode <http://rdfh.ch/lists/0001/root-node> ;
            |    knora-base:listNodePosition 0 .
            |<http://rdfh.ch/lists/0001/child-node> knora-base:listNodeName "childNode" .
            |<http://rdfh.ch/lists/0001/child-node> rdfs:label "Child Node"@en .
            |<http://rdfh.ch/lists/0001/child-node> rdfs:comment "A child node"@en . } }
            |WHERE {}""".stripMargin,
      )
    },
    test("should produce correct query for child node at position 5") {
      val labels   = Labels.unsafeFrom(List(StringLiteralV2.from("Node Five", EN)))
      val comments = Comments.unsafeFrom(List(StringLiteralV2.from("Fifth node", EN)))
      val position = Position.unsafeFrom(5)

      val query = CreateListNodeQuery.build(
        knoraProject = testProject,
        node = childNodeIri,
        parent = Some((parentNodeIri, rootNodeIri, position)),
        name = None,
        labels = labels,
        comments = comments,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/lists/0001/child-node> a knora-base:ListNode .
            |<http://rdfh.ch/lists/0001/parent-node> knora-base:hasSubListNode <http://rdfh.ch/lists/0001/child-node> .
            |<http://rdfh.ch/lists/0001/child-node> knora-base:hasRootNode <http://rdfh.ch/lists/0001/root-node> ;
            |    knora-base:listNodePosition 5 .
            |<http://rdfh.ch/lists/0001/child-node> rdfs:label "Node Five"@en .
            |<http://rdfh.ch/lists/0001/child-node> rdfs:comment "Fifth node"@en . } }
            |WHERE {}""".stripMargin,
      )
    },
    test("should produce correct query for root node with multilingual labels and comments") {
      val labels = Labels.unsafeFrom(
        List(
          StringLiteralV2.from("English Label", EN),
          StringLiteralV2.from("Deutsche Bezeichnung", DE),
          StringLiteralV2.from("Étiquette française", FR),
          StringLiteralV2.from("Etichetta italiana", IT),
        ),
      )
      val comments = Comments.unsafeFrom(
        List(
          StringLiteralV2.from("English comment", EN),
          StringLiteralV2.from("Deutscher Kommentar", DE),
          StringLiteralV2.from("Commentaire français", FR),
        ),
      )

      val query = CreateListNodeQuery.build(
        knoraProject = testProject,
        node = rootNodeIri,
        parent = None,
        name = Some(ListName.unsafeFrom("multilingualList")),
        labels = labels,
        comments = comments,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/lists/0001/root-node> a knora-base:ListNode .
            |<http://rdfh.ch/lists/0001/root-node> knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
            |    knora-base:isRootNode true .
            |<http://rdfh.ch/lists/0001/root-node> knora-base:listNodeName "multilingualList" .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:label "English Label"@en .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:label "Deutsche Bezeichnung"@de .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:label "Étiquette française"@fr .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:label "Etichetta italiana"@it .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:comment "English comment"@en .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:comment "Deutscher Kommentar"@de .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:comment "Commentaire français"@fr . } }
            |WHERE {}""".stripMargin,
      )
    },
    test("should produce correct query for different project") {
      val otherProject = KnoraProject(
        ProjectIri.unsafeFrom("http://rdfh.ch/projects/00FF"),
        Shortname.unsafeFrom("images"),
        Shortcode.unsafeFrom("00FF"),
        None,
        NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.from("Images project"))),
        List.empty,
        None,
        Status.Active,
        SelfJoin.CannotJoin,
        RestrictedView.default,
        Set.empty,
        Set.empty,
      )
      val nodeIri  = ListIri.unsafeFrom("http://rdfh.ch/lists/00FF/image-types")
      val labels   = Labels.unsafeFrom(List(StringLiteralV2.from("Image Types", EN)))
      val comments = Comments.unsafeFrom(List(StringLiteralV2.from("Types of images", EN)))

      val query = CreateListNodeQuery.build(
        knoraProject = otherProject,
        node = nodeIri,
        parent = None,
        name = Some(ListName.unsafeFrom("imageTypes")),
        labels = labels,
        comments = comments,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |INSERT { GRAPH <http://www.knora.org/data/00FF/images> { <http://rdfh.ch/lists/00FF/image-types> a knora-base:ListNode .
            |<http://rdfh.ch/lists/00FF/image-types> knora-base:attachedToProject <http://rdfh.ch/projects/00FF> ;
            |    knora-base:isRootNode true .
            |<http://rdfh.ch/lists/00FF/image-types> knora-base:listNodeName "imageTypes" .
            |<http://rdfh.ch/lists/00FF/image-types> rdfs:label "Image Types"@en .
            |<http://rdfh.ch/lists/00FF/image-types> rdfs:comment "Types of images"@en . } }
            |WHERE {}""".stripMargin,
      )
    },
    test("should handle labels with special characters") {
      val labels = Labels.unsafeFrom(
        List(
          StringLiteralV2.from("Label with \"quotes\" and 'apostrophes'", EN),
        ),
      )
      val comments = Comments.unsafeFrom(
        List(
          StringLiteralV2.from("Comment with special chars", EN),
        ),
      )

      val query = CreateListNodeQuery.build(
        knoraProject = testProject,
        node = rootNodeIri,
        parent = None,
        name = None,
        labels = labels,
        comments = comments,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/lists/0001/root-node> a knora-base:ListNode .
            |<http://rdfh.ch/lists/0001/root-node> knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
            |    knora-base:isRootNode true .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:label "Label with \"quotes\" and \'apostrophes\'"@en .
            |<http://rdfh.ch/lists/0001/root-node> rdfs:comment "Comment with special chars"@en . } }
            |WHERE {}""".stripMargin,
      )
    },
  )
}
