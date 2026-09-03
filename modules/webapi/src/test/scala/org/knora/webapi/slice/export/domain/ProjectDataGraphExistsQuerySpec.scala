/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.TestDataFactory

@RunWith(classOf[DspZTestJUnitRunner])
class ProjectDataGraphExistsQuerySpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] = suite("ProjectDataGraphExistsQuerySpec")(
    // Exact-string regression guard for the create-only precondition. The ASK excludes list-node subjects
    // (`FILTER NOT EXISTS { ?s a knora-base:ListNode }`) inside the project data graph, so a lists-only graph
    // answers false. The `?s ?p ?o` base triple stays present: rdf4j 5.2.2 drops a FILTER NOT EXISTS that is the
    // only WHERE pattern (issue #5561, fixed 5.3.0).
    test("build excludes list-node subjects, scoped to the project data graph") {
      val expected =
        """
          |ASK
          |WHERE {
          |  GRAPH <http://www.knora.org/data/0001/shortname> { ?s ?p ?o .
          |FILTER NOT EXISTS { ?s a <http://www.knora.org/ontology/knora-base#ListNode> . } }
          |}
          |""".stripMargin
      assertTrue(ProjectDataGraphExistsQuery.build(TestDataFactory.someProject).sparql == expected)
    },
  )
}
