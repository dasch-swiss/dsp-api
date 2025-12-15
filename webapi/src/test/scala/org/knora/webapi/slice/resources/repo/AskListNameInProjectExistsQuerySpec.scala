/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListName
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object AskListNameInProjectExistsQuerySpec extends ZIOSpecDefault {

  private val testListName    = ListName.unsafeFrom("testList")
  private val testProjectIri  = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val testListName2   = ListName.unsafeFrom("my-special-list")
  private val testProjectIri2 = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0042")

  override def spec: Spec[TestEnvironment, Any] = suite("AskListNameInProjectExistsQuerySpec")(
    test("should produce correct ASK query with basic list name and project IRI") {
      val actual: Ask = AskListNameInProjectExistsQuery.build(testListName, testProjectIri)

      assertTrue(
        actual.sparql ==
          """ASK { ?rootNode <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#ListNode> ;
            |    <http://www.knora.org/ontology/knora-base#attachedToProject> <http://rdfh.ch/projects/0001> ;
            |    <http://www.knora.org/ontology/knora-base#hasSubListNode>* ?node .
            |?node <http://www.knora.org/ontology/knora-base#listNodeName> "testList" . } """.stripMargin,
      )
    },
    test("should produce correct ASK query with list name containing hyphens") {
      val actual: Ask = AskListNameInProjectExistsQuery.build(testListName2, testProjectIri2)

      assertTrue(
        actual.sparql ==
          """ASK { ?rootNode <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#ListNode> ;
            |    <http://www.knora.org/ontology/knora-base#attachedToProject> <http://rdfh.ch/projects/0042> ;
            |    <http://www.knora.org/ontology/knora-base#hasSubListNode>* ?node .
            |?node <http://www.knora.org/ontology/knora-base#listNodeName> "my-special-list" . } """.stripMargin,
      )
    },
    test("should produce correct ASK query with list name containing special characters") {
      val specialListName = ListName.unsafeFrom("list_with_underscores_123")
      val actual: Ask     = AskListNameInProjectExistsQuery.build(specialListName, testProjectIri)

      assertTrue(
        actual.sparql ==
          """ASK { ?rootNode <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#ListNode> ;
            |    <http://www.knora.org/ontology/knora-base#attachedToProject> <http://rdfh.ch/projects/0001> ;
            |    <http://www.knora.org/ontology/knora-base#hasSubListNode>* ?node .
            |?node <http://www.knora.org/ontology/knora-base#listNodeName> "list_with_underscores_123" . } """.stripMargin,
      )
    },
    test("should produce correct ASK query with different project shortcode") {
      val listName    = ListName.unsafeFrom("images")
      val projectIri  = ProjectIri.unsafeFrom("http://rdfh.ch/projects/00FF")
      val actual: Ask = AskListNameInProjectExistsQuery.build(listName, projectIri)

      assertTrue(
        actual.sparql ==
          """ASK { ?rootNode <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://www.knora.org/ontology/knora-base#ListNode> ;
            |    <http://www.knora.org/ontology/knora-base#attachedToProject> <http://rdfh.ch/projects/00FF> ;
            |    <http://www.knora.org/ontology/knora-base#hasSubListNode>* ?node .
            |?node <http://www.knora.org/ontology/knora-base#listNodeName> "images" . } """.stripMargin,
      )
    },
  )
}
