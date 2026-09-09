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
class ReferencedUserIrisQuerySpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val q = QueryFactory.create(query)
    q.getPrefixMapping.clearNsPrefixMap()
    q.toString
  }

  private val testDataGraph = InternalIri("http://www.knora.org/data/0001/anything")

  override def spec: Spec[TestEnvironment, Any] = suite("ReferencedUserIrisQuerySpec")(
    test("build renders the referenced user IRIs SELECT DISTINCT query") {
      val expected =
        """SELECT DISTINCT ?user
          |WHERE { GRAPH <http://www.knora.org/data/0001/anything> { ?resource <http://www.knora.org/ontology/knora-base#attachedToUser> ?user . } }
          |""".stripMargin
      assertTrue(canonical(ReferencedUserIrisQuery.build(testDataGraph).sparql) == canonical(expected))
    },
  )
}
