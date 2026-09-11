/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class GetAllOntologiesMetadataQuerySpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  override def spec: Spec[TestEnvironment, Any] = suite("GetAllOntologiesMetadataQuerySpec")(
    test("should produce the same SELECT query as the legacy builder") {
      val actual = GetAllOntologiesMetadataQuery.build.sparql
      assertTrue(
        canonical(actual) == canonical(
          """PREFIX owl: <http://www.w3.org/2002/07/owl#>
            |SELECT ?ontologyGraph ?ontologyIri ?ontologyPred ?ontologyObj
            |WHERE { GRAPH ?ontologyGraph { ?ontologyIri a owl:Ontology ;
            |    ?ontologyPred ?ontologyObj . } }""".stripMargin,
        ),
      )
    },
  )
}
