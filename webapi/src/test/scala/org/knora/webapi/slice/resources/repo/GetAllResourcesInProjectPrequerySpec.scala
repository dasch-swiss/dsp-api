/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

object GetAllResourcesInProjectPrequerySpec extends ZIOSpecDefault {
  override val spec: Spec[Any, Nothing] = suite("GetAllResourcesInProjectPrequery")(
    test("build should produce the expected SPARQL query") {
      val projectIri = "http://rdfh.ch/projects/0001"
      val actual     = GetAllResourcesInProjectPrequery.build(projectIri).getQueryString.strip()
      val expected =
        """|PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
           |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
           |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |
           |SELECT DISTINCT ?resource
           |WHERE {
           |    ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> .
           |    ?resourceType rdfs:subClassOf* knora-base:Resource .
           |    ?resource rdf:type ?resourceType .
           |    ?resource knora-base:creationDate ?creationDate.
           |}
           |ORDER BY DESC(?creationDate)""".stripMargin
      assertTrue(actual == expected)
    },
  )
}
