/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.standoff.repo

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class GetXslTransformationMetadataQuerySpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  override def spec: Spec[TestEnvironment, Any] = suite("GetXslTransformationMetadataQuerySpec")(
    test("should produce the same SELECT query as the legacy builder") {
      val actual = GetXslTransformationMetadataQuery.build("http://rdfh.ch/0001/thing-with-xsl").sparql
      assertTrue(
        canonical(actual) == canonical(
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            |SELECT ?resourceClass ?fileValueIri ?internalFilename ?internalMimeType ?projectIri
            |WHERE { <http://rdfh.ch/0001/thing-with-xsl> rdf:type ?resourceClass ;
            |    knora-base:attachedToProject ?projectIri ;
            |    knora-base:hasTextFileValue ?fileValueIri .
            |?fileValueIri knora-base:internalFilename ?internalFilename ;
            |    knora-base:internalMimeType ?internalMimeType . }""".stripMargin,
        ),
      )
    },
  )
}
