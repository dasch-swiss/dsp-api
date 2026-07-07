/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri

object GetListNodeQuerySpec extends ZIOSpecDefault {

  private val testNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/test-node")

  override def spec: Spec[TestEnvironment, Any] = suite("GetListNodeQuerySpec")(
    test("should produce correct CONSTRUCT query for list node") {
      val actual = GetListNodeQuery.build(testNodeIri).getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |CONSTRUCT { <http://rdfh.ch/lists/0001/test-node> ?p ?o . }
            |WHERE { <http://rdfh.ch/lists/0001/test-node> a knora-base:ListNode ;
            |    ?p ?o . }
            |""".stripMargin,
      )
    },
    test("should produce correct query for different node IRI") {
      val differentNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/another-node")
      val actual           = GetListNodeQuery.build(differentNodeIri).getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |CONSTRUCT { <http://rdfh.ch/lists/0001/another-node> ?p ?o . }
            |WHERE { <http://rdfh.ch/lists/0001/another-node> a knora-base:ListNode ;
            |    ?p ?o . }
            |""".stripMargin,
      )
    },
    test("should produce correct query for node with different project") {
      val nodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0803/book-list-node")
      val actual  = GetListNodeQuery.build(nodeIri).getQueryString
      assertTrue(
        actual ==
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |CONSTRUCT { <http://rdfh.ch/lists/0803/book-list-node> ?p ?o . }
            |WHERE { <http://rdfh.ch/lists/0803/book-list-node> a knora-base:ListNode ;
            |    ?p ?o . }
            |""".stripMargin,
      )
    },
  )
}
