/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.common.ResourceIri

@RunWith(classOf[DspZTestJUnitRunner])
class IsResourceInUseQuerySpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  override def spec: Spec[TestEnvironment, Any] = suite("IsResourceInUseQuery")(
    test("pins both selective incoming-reference probes in GRAPH-scoped subqueries") {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing")
      val dataGraph   = "http://www.knora.org/data/0001/anything"
      val actual      = IsResourceInUseQuery.build(resourceIri, dataGraph).sparql
      val expected    =
        """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |SELECT DISTINCT ?other
          |WHERE { { { { SELECT ?other
          |WHERE { GRAPH <http://www.knora.org/data/0001/anything> { ?other ?p <http://rdfh.ch/0001/a-thing> . } }
          | }
          |GRAPH <http://www.knora.org/data/0001/anything> { ?other knora-base:isDeleted false . } } UNION { { SELECT ?other ?valueNode
          |WHERE { GRAPH <http://www.knora.org/data/0001/anything> { ?valueNode knora-base:isRegionPreviewOf <http://rdfh.ch/0001/a-thing> .
          |?other ?valueProp ?valueNode . } }
          | }
          |GRAPH <http://www.knora.org/data/0001/anything> { ?other knora-base:isDeleted false .
          |?valueNode knora-base:isDeleted false . } }
          |FILTER NOT EXISTS { GRAPH <http://www.knora.org/data/0001/anything> { ?other a knora-base:LinkValue . } }
          |FILTER ( REGEX( STR( ?other ), "^http://rdfh\\.ch/[0-9A-Fa-f]{4}/[A-Za-z0-9_-]+$" ) ) } }
          |""".stripMargin
      assertTrue(
        canonical(actual) == canonical(expected),
        // The two GRAPH-scoped subqueries are the performance barrier (DEV-6885); keep them pinned
        // textually as well, so a refactor cannot quietly flatten them away.
        actual.contains("SELECT ?other WHERE { GRAPH <http://www.knora.org/data/0001/anything> {"),
        actual.contains("SELECT ?other ?valueNode"),
        actual.contains("FILTER NOT EXISTS { GRAPH <http://www.knora.org/data/0001/anything> {"),
      )
    },
  )
}
