/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter

object GetResourcesByClassInProjectPrequerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testProjectIri       = "http://rdfh.ch/projects/0001"
  private val testResourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri
  private val testOrderByProperty  = "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri
  private val testValuePredicate   = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri

  override def spec: Spec[Any, Nothing] = suite("GetResourcesByClassInProjectPrequery")(
    test("build without orderByProperty should produce the expected SPARQL query") {
      val actual = GetResourcesByClassInProjectPrequery
        .build(
          projectIri = testProjectIri,
          resourceClassIri = testResourceClassIri,
          maybeOrderByProperty = None,
          maybeOrderByValuePredicate = None,
          offset = 0,
          limit = 25,
        )
        .getQueryString
        .strip()
      val expected =
        """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
           |SELECT DISTINCT ?resource
           |WHERE { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
           |    rdf:type <http://www.knora.org/ontology/0001/anything#Thing> .
           |FILTER NOT EXISTS { ?resource knora-base:isDeleted true . } }
           |ORDER BY ASC( ?resource )
           |LIMIT 25
           |OFFSET 0""".stripMargin
      assertTrue(actual == expected)
    },
    test("build with orderByProperty should produce the expected SPARQL query") {
      val actual = GetResourcesByClassInProjectPrequery
        .build(
          projectIri = testProjectIri,
          resourceClassIri = testResourceClassIri,
          maybeOrderByProperty = Some(testOrderByProperty),
          maybeOrderByValuePredicate = Some(testValuePredicate),
          offset = 0,
          limit = 25,
        )
        .getQueryString
        .strip()
      val expected =
        """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
           |SELECT DISTINCT ?resource
           |WHERE { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
           |    rdf:type <http://www.knora.org/ontology/0001/anything#Thing> .
           |FILTER NOT EXISTS { ?resource knora-base:isDeleted true . }
           |OPTIONAL { ?resource <http://www.knora.org/ontology/0001/anything#hasInteger> ?orderByValue .
           |?orderByValue knora-base:valueHasInteger ?orderByValueLiteral .
           |FILTER NOT EXISTS { ?resource <http://www.knora.org/ontology/0001/anything#hasInteger> ?otherOrderByValue .
           |{ ?otherOrderByValue knora-base:valueHasInteger ?otherOrderByValueLiteral .
           |FILTER ( ?otherOrderByValueLiteral < ?orderByValueLiteral ) } } } }
           |ORDER BY ASC( ?orderByValueLiteral ) ASC( ?resource )
           |LIMIT 25
           |OFFSET 0""".stripMargin
      assertTrue(actual == expected)
    },
    test("build with non-zero offset should include correct offset") {
      val actual = GetResourcesByClassInProjectPrequery
        .build(
          projectIri = testProjectIri,
          resourceClassIri = testResourceClassIri,
          maybeOrderByProperty = None,
          maybeOrderByValuePredicate = None,
          offset = 50,
          limit = 25,
        )
        .getQueryString
        .strip()
      val expected =
        """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
           |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
           |SELECT DISTINCT ?resource
           |WHERE { ?resource knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
           |    rdf:type <http://www.knora.org/ontology/0001/anything#Thing> .
           |FILTER NOT EXISTS { ?resource knora-base:isDeleted true . } }
           |ORDER BY ASC( ?resource )
           |LIMIT 25
           |OFFSET 50""".stripMargin
      assertTrue(actual == expected)
    },
  )
}
