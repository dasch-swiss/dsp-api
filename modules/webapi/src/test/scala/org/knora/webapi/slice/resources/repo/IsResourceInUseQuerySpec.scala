/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.common.ResourceIri

@RunWith(classOf[DspZTestJUnitRunner])
class IsResourceInUseQuerySpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] = suite("IsResourceInUseQuery")(
    test("pins both selective incoming-reference probes in GRAPH-scoped subqueries") {
      val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing")
      val dataGraph   = "http://www.knora.org/data/0001/anything"
      val actual      = IsResourceInUseQuery.build(resourceIri, dataGraph).getQueryString
      val expected    =
        """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |SELECT DISTINCT ?other
          |WHERE { { { SELECT ?other
          |WHERE { GRAPH <http://www.knora.org/data/0001/anything> { ?other ?p <http://rdfh.ch/0001/a-thing> . } }
          | }
          |GRAPH <http://www.knora.org/data/0001/anything> { ?other knora-base:isDeleted false . } } UNION { { SELECT ?other ?valueNode
          |WHERE { GRAPH <http://www.knora.org/data/0001/anything> { ?valueNode knora-base:isRegionPreviewOf <http://rdfh.ch/0001/a-thing> .
          |?other ?valueProp ?valueNode . } }
          | }
          |GRAPH <http://www.knora.org/data/0001/anything> { ?other knora-base:isDeleted false .
          |?valueNode knora-base:isDeleted false . } }
          |FILTER NOT EXISTS { GRAPH <http://www.knora.org/data/0001/anything> { ?other a knora-base:LinkValue . } } }
          |""".stripMargin
      assertTrue(actual == expected)
    },
  )
}
