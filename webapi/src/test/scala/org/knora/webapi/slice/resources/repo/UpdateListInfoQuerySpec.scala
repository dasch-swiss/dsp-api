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
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.common.domain.LanguageCode.DE
import org.knora.webapi.slice.common.domain.LanguageCode.EN
import org.knora.webapi.slice.common.domain.LanguageCode.FR

object UpdateListInfoQuerySpec extends ZIOSpecDefault {

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

  private val listNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/test-list")

  override def spec: Spec[TestEnvironment, Any] = suite("UpdateListInfoQuerySpec")(
    test("should produce correct query to update only labels") {
      val labels = Labels.unsafeFrom(
        List(
          StringLiteralV2.from("Updated Label", EN),
          StringLiteralV2.from("Étiquette mise à jour", FR),
        ),
      )

      val query = UpdateListInfoQuery.build(
        project = testProject,
        nodeIri = listNodeIri,
        name = None,
        labels = Some(labels),
        comments = None,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |WITH <http://www.knora.org/data/0001/anything>
            |DELETE { <http://rdfh.ch/lists/0001/test-list> rdfs:label ?currentLabels . }
            |INSERT { <http://rdfh.ch/lists/0001/test-list> rdfs:label "Updated Label"@en .
            |<http://rdfh.ch/lists/0001/test-list> rdfs:label "Étiquette mise à jour"@fr . }
            |WHERE { <http://rdfh.ch/lists/0001/test-list> a knora-base:ListNode .
            |OPTIONAL { <http://rdfh.ch/lists/0001/test-list> rdfs:label ?currentLabels . } }""".stripMargin,
      )
    },
    test("should produce correct query to update only name") {
      val name = ListName.unsafeFrom("updatedListName")

      val query = UpdateListInfoQuery.build(
        project = testProject,
        nodeIri = listNodeIri,
        name = Some(name),
        labels = None,
        comments = None,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |WITH <http://www.knora.org/data/0001/anything>
            |DELETE { <http://rdfh.ch/lists/0001/test-list> knora-base:listNodeName ?currentName . }
            |INSERT { <http://rdfh.ch/lists/0001/test-list> knora-base:listNodeName "updatedListName" . }
            |WHERE { <http://rdfh.ch/lists/0001/test-list> a knora-base:ListNode .
            |OPTIONAL { <http://rdfh.ch/lists/0001/test-list> knora-base:listNodeName ?currentName . } }""".stripMargin,
      )
    },
    test("should produce correct query to update only comments") {
      val comments = Comments.unsafeFrom(
        List(
          StringLiteralV2.from("Updated comment", EN),
        ),
      )

      val query = UpdateListInfoQuery.build(
        project = testProject,
        nodeIri = listNodeIri,
        name = None,
        labels = None,
        comments = Some(comments),
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |WITH <http://www.knora.org/data/0001/anything>
            |DELETE { <http://rdfh.ch/lists/0001/test-list> rdfs:comment ?currentComments . }
            |INSERT { <http://rdfh.ch/lists/0001/test-list> rdfs:comment "Updated comment"@en . }
            |WHERE { <http://rdfh.ch/lists/0001/test-list> a knora-base:ListNode .
            |OPTIONAL { <http://rdfh.ch/lists/0001/test-list> rdfs:comment ?currentComments . } }""".stripMargin,
      )
    },
    test("should produce correct query to update all fields") {
      val name   = ListName.unsafeFrom("completeUpdate")
      val labels = Labels.unsafeFrom(
        List(
          StringLiteralV2.from("Complete Label", EN),
          StringLiteralV2.from("Label complet", FR),
        ),
      )
      val comments = Comments.unsafeFrom(
        List(
          StringLiteralV2.from("Complete comment", EN),
          StringLiteralV2.from("Commentaire complet", FR),
        ),
      )

      val query = UpdateListInfoQuery.build(
        project = testProject,
        nodeIri = listNodeIri,
        name = Some(name),
        labels = Some(labels),
        comments = Some(comments),
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |WITH <http://www.knora.org/data/0001/anything>
            |DELETE { <http://rdfh.ch/lists/0001/test-list> rdfs:label ?currentLabels .
            |<http://rdfh.ch/lists/0001/test-list> knora-base:listNodeName ?currentName .
            |<http://rdfh.ch/lists/0001/test-list> rdfs:comment ?currentComments . }
            |INSERT { <http://rdfh.ch/lists/0001/test-list> rdfs:label "Complete Label"@en .
            |<http://rdfh.ch/lists/0001/test-list> rdfs:label "Label complet"@fr .
            |<http://rdfh.ch/lists/0001/test-list> knora-base:listNodeName "completeUpdate" .
            |<http://rdfh.ch/lists/0001/test-list> rdfs:comment "Complete comment"@en .
            |<http://rdfh.ch/lists/0001/test-list> rdfs:comment "Commentaire complet"@fr . }
            |WHERE { <http://rdfh.ch/lists/0001/test-list> a knora-base:ListNode .
            |OPTIONAL { <http://rdfh.ch/lists/0001/test-list> rdfs:label ?currentLabels . }
            |OPTIONAL { <http://rdfh.ch/lists/0001/test-list> knora-base:listNodeName ?currentName . }
            |OPTIONAL { <http://rdfh.ch/lists/0001/test-list> rdfs:comment ?currentComments . } }""".stripMargin,
      )
    },
    test("should produce correct query to update labels and name") {
      val name   = ListName.unsafeFrom("partialUpdate")
      val labels = Labels.unsafeFrom(
        List(
          StringLiteralV2.from("Partial Label", EN),
        ),
      )

      val query = UpdateListInfoQuery.build(
        project = testProject,
        nodeIri = listNodeIri,
        name = Some(name),
        labels = Some(labels),
        comments = None,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |WITH <http://www.knora.org/data/0001/anything>
            |DELETE { <http://rdfh.ch/lists/0001/test-list> rdfs:label ?currentLabels .
            |<http://rdfh.ch/lists/0001/test-list> knora-base:listNodeName ?currentName . }
            |INSERT { <http://rdfh.ch/lists/0001/test-list> rdfs:label "Partial Label"@en .
            |<http://rdfh.ch/lists/0001/test-list> knora-base:listNodeName "partialUpdate" . }
            |WHERE { <http://rdfh.ch/lists/0001/test-list> a knora-base:ListNode .
            |OPTIONAL { <http://rdfh.ch/lists/0001/test-list> rdfs:label ?currentLabels . }
            |OPTIONAL { <http://rdfh.ch/lists/0001/test-list> knora-base:listNodeName ?currentName . } }""".stripMargin,
      )
    },
    test("should produce correct query to update name and comments") {
      val name     = ListName.unsafeFrom("nameAndComments")
      val comments = Comments.unsafeFrom(
        List(
          StringLiteralV2.from("Name and comments update", EN),
        ),
      )

      val query = UpdateListInfoQuery.build(
        project = testProject,
        nodeIri = listNodeIri,
        name = Some(name),
        labels = None,
        comments = Some(comments),
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |WITH <http://www.knora.org/data/0001/anything>
            |DELETE { <http://rdfh.ch/lists/0001/test-list> knora-base:listNodeName ?currentName .
            |<http://rdfh.ch/lists/0001/test-list> rdfs:comment ?currentComments . }
            |INSERT { <http://rdfh.ch/lists/0001/test-list> knora-base:listNodeName "nameAndComments" .
            |<http://rdfh.ch/lists/0001/test-list> rdfs:comment "Name and comments update"@en . }
            |WHERE { <http://rdfh.ch/lists/0001/test-list> a knora-base:ListNode .
            |OPTIONAL { <http://rdfh.ch/lists/0001/test-list> knora-base:listNodeName ?currentName . }
            |OPTIONAL { <http://rdfh.ch/lists/0001/test-list> rdfs:comment ?currentComments . } }""".stripMargin,
      )
    },
    test("should produce correct query to update labels and comments") {
      val labels = Labels.unsafeFrom(
        List(
          StringLiteralV2.from("Labels and Comments", EN),
        ),
      )
      val comments = Comments.unsafeFrom(
        List(
          StringLiteralV2.from("Updated both", EN),
        ),
      )

      val query = UpdateListInfoQuery.build(
        project = testProject,
        nodeIri = listNodeIri,
        name = None,
        labels = Some(labels),
        comments = Some(comments),
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |WITH <http://www.knora.org/data/0001/anything>
            |DELETE { <http://rdfh.ch/lists/0001/test-list> rdfs:label ?currentLabels .
            |<http://rdfh.ch/lists/0001/test-list> rdfs:comment ?currentComments . }
            |INSERT { <http://rdfh.ch/lists/0001/test-list> rdfs:label "Labels and Comments"@en .
            |<http://rdfh.ch/lists/0001/test-list> rdfs:comment "Updated both"@en . }
            |WHERE { <http://rdfh.ch/lists/0001/test-list> a knora-base:ListNode .
            |OPTIONAL { <http://rdfh.ch/lists/0001/test-list> rdfs:label ?currentLabels . }
            |OPTIONAL { <http://rdfh.ch/lists/0001/test-list> rdfs:comment ?currentComments . } }""".stripMargin,
      )
    },
    test("should produce correct query with multilingual labels") {
      val labels = Labels.unsafeFrom(
        List(
          StringLiteralV2.from("English Label", EN),
          StringLiteralV2.from("Deutsche Bezeichnung", DE),
          StringLiteralV2.from("Label français", FR),
        ),
      )

      val query = UpdateListInfoQuery.build(
        project = testProject,
        nodeIri = listNodeIri,
        name = None,
        labels = Some(labels),
        comments = None,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |WITH <http://www.knora.org/data/0001/anything>
            |DELETE { <http://rdfh.ch/lists/0001/test-list> rdfs:label ?currentLabels . }
            |INSERT { <http://rdfh.ch/lists/0001/test-list> rdfs:label "English Label"@en .
            |<http://rdfh.ch/lists/0001/test-list> rdfs:label "Deutsche Bezeichnung"@de .
            |<http://rdfh.ch/lists/0001/test-list> rdfs:label "Label français"@fr . }
            |WHERE { <http://rdfh.ch/lists/0001/test-list> a knora-base:ListNode .
            |OPTIONAL { <http://rdfh.ch/lists/0001/test-list> rdfs:label ?currentLabels . } }""".stripMargin,
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
      val nodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/00FF/image-list")
      val name    = ListName.unsafeFrom("imageCategories")

      val query = UpdateListInfoQuery.build(
        project = otherProject,
        nodeIri = nodeIri,
        name = Some(name),
        labels = None,
        comments = None,
      )

      assertTrue(
        query.getQueryString ==
          """PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |WITH <http://www.knora.org/data/00FF/images>
            |DELETE { <http://rdfh.ch/lists/00FF/image-list> knora-base:listNodeName ?currentName . }
            |INSERT { <http://rdfh.ch/lists/00FF/image-list> knora-base:listNodeName "imageCategories" . }
            |WHERE { <http://rdfh.ch/lists/00FF/image-list> a knora-base:ListNode .
            |OPTIONAL { <http://rdfh.ch/lists/00FF/image-list> knora-base:listNodeName ?currentName . } }""".stripMargin,
      )
    },
  )
}
