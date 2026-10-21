/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.domain.InternalIri

@RunWith(classOf[DspZTestJUnitRunner])
class GraphPatternSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val luceneStatement = StatementPattern(
    QueryVariable("mainRes"),
    IriRef(OntologyConstants.Fuseki.luceneQueryPredicate.toSmartIri),
    LuceneQueryArgs("Zeitglöcklein", 10000),
  )

  override def spec: Spec[TestEnvironment, Any] = suite("GraphPattern.toSparql")(
    test("wraps the statement in a GRAPH block naming the graph") {
      val pattern = GraphPattern(InternalIri("http://www.knora.org/data/0803/incunabula"), luceneStatement)
      assertTrue(
        pattern.toSparql ==
          """GRAPH <http://www.knora.org/data/0803/incunabula> {
            |?mainRes <http://jena.apache.org/text#query> ("Zeitglöcklein" 10000) .
            |}
            |""".stripMargin,
      )
    },
  )
}
