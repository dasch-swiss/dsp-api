/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri

@RunWith(classOf[DspZTestJUnitRunner])
class IsNodeUsedQuerySpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  private val testNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/test-node")

  override def spec: Spec[TestEnvironment, Any] = suite("IsNodeUsedQuerySpec")(
    test("should produce correct ASK query for a node IRI") {
      val actual = IsNodeUsedQuery.build(testNodeIri).sparql
      assertTrue(
        canonical(actual) == canonical(
          """ASK
            |WHERE {
            |  {
            |    ?s <http://www.knora.org/ontology/salsah-gui#guiAttribute> "hlist=<http://rdfh.ch/lists/0001/test-node>" .
            |  } UNION {
            |    ?s <http://www.knora.org/ontology/knora-base#valueHasListNode> <http://rdfh.ch/lists/0001/test-node> .
            |  }
            |}""".stripMargin,
        ),
      )
    },
    test("should produce correct ASK query for a different node IRI") {
      val differentNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0803/another-node")
      val actual           = IsNodeUsedQuery.build(differentNodeIri).sparql
      assertTrue(
        canonical(actual) == canonical(
          """ASK
            |WHERE {
            |  {
            |    ?s <http://www.knora.org/ontology/salsah-gui#guiAttribute> "hlist=<http://rdfh.ch/lists/0803/another-node>" .
            |  } UNION {
            |    ?s <http://www.knora.org/ontology/knora-base#valueHasListNode> <http://rdfh.ch/lists/0803/another-node> .
            |  }
            |}""".stripMargin,
        ),
      )
    },
  )
}
