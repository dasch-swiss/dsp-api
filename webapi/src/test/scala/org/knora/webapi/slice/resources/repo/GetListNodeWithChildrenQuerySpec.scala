/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri

object GetListNodeWithChildrenQuerySpec extends ZIOSpecDefault {

  private val testNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/test-node")

  override def spec: Spec[TestEnvironment, Any] = suite("GetListNodeWithChildrenQuerySpec")(
    test("should produce correct CONSTRUCT query for list node with children") {
      val actual = GetListNodeWithChildrenQuery.build(testNodeIri).getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |CONSTRUCT { ?node ?p ?o . }
            |WHERE { <http://rdfh.ch/lists/0001/test-node> knora-base:hasSubListNode* ?node .
            |?node a knora-base:ListNode ;
            |    ?p ?o . }
            |""".stripMargin,
      )
    },
    test("should produce correct query for different node IRI") {
      val differentNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/parent-node")
      val actual           = GetListNodeWithChildrenQuery.build(differentNodeIri).getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |CONSTRUCT { ?node ?p ?o . }
            |WHERE { <http://rdfh.ch/lists/0001/parent-node> knora-base:hasSubListNode* ?node .
            |?node a knora-base:ListNode ;
            |    ?p ?o . }
            |""".stripMargin,
      )
    },
    test("should produce correct query for node with different project") {
      val nodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0803/book-list-root")
      val actual  = GetListNodeWithChildrenQuery.build(nodeIri).getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |CONSTRUCT { ?node ?p ?o . }
            |WHERE { <http://rdfh.ch/lists/0803/book-list-root> knora-base:hasSubListNode* ?node .
            |?node a knora-base:ListNode ;
            |    ?p ?o . }
            |""".stripMargin,
      )
    },
  )
}
