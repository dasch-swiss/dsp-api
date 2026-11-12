/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.common.domain.InternalIri

@RunWith(classOf[DspZTestJUnitRunner])
class ProjectListNodesQuerySpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val q = QueryFactory.create(query)
    q.getPrefixMapping.clearNsPrefixMap()
    q.toString
  }

  override def spec: Spec[TestEnvironment, Any] = suite("ProjectListNodesQuerySpec")(
    test("build renders the list nodes CONSTRUCT query for the data graph") {
      val expected =
        """CONSTRUCT { ?node ?p ?o . }
          |WHERE { GRAPH <http://www.knora.org/data/0803/incunabula> {
          |  ?node a <http://www.knora.org/ontology/knora-base#ListNode> ; ?p ?o . } }
          |""".stripMargin
      val graph = InternalIri("http://www.knora.org/data/0803/incunabula")
      assertTrue(canonical(ProjectListNodesQuery.build(graph).sparql) == canonical(expected))
    },
  )
}
