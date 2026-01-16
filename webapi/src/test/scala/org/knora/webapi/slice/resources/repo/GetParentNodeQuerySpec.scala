/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri

object GetParentNodeQuerySpec extends ZIOSpecDefault {

  private val testNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/test-node")

  override def spec: Spec[TestEnvironment, Any] = suite("GetParentNodeQuerySpec")(
    test("should produce correct CONSTRUCT query for parent node") {
      val actual = GetParentNodeQuery.build(testNodeIri).getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |CONSTRUCT { ?s ?p ?o . }
            |WHERE { ?s a knora-base:ListNode ;
            |    knora-base:hasSubListNode <http://rdfh.ch/lists/0001/test-node> ;
            |    ?p ?o . }
            |""".stripMargin,
      )
    },
    test("should produce correct query for different node IRI") {
      val differentNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/child-node")
      val actual           = GetParentNodeQuery.build(differentNodeIri).getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |CONSTRUCT { ?s ?p ?o . }
            |WHERE { ?s a knora-base:ListNode ;
            |    knora-base:hasSubListNode <http://rdfh.ch/lists/0001/child-node> ;
            |    ?p ?o . }
            |""".stripMargin,
      )
    },
    test("should produce correct query for node with different project") {
      val nodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0803/book-category")
      val actual  = GetParentNodeQuery.build(nodeIri).getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |CONSTRUCT { ?s ?p ?o . }
            |WHERE { ?s a knora-base:ListNode ;
            |    knora-base:hasSubListNode <http://rdfh.ch/lists/0803/book-category> ;
            |    ?p ?o . }
            |""".stripMargin,
      )
    },
  )
}
