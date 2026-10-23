/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.domain.InternalIri

@RunWith(classOf[DspZTestJUnitRunner])
class GetResourceInfoQuerySpec extends ZIOSpecDefault {

  private val project       = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val resourceClass = InternalIri("http://www.knora.org/ontology/0001/anything#Thing")
  private val cutoff        = Instant.parse("2026-10-23T08:00:00.123456Z")

  private def build(rc: Option[InternalIri], c: Option[Instant]) = GetResourceInfoQuery.build(project, rc, c).sparql

  private def parses(query: String) = QueryFactory.create(query) != null

  override def spec: Spec[TestEnvironment, Any] = suite("GetResourceInfoQuery")(
    test("no class, no cutoff") {
      val actual = build(None, None)
      assertTrue(
        actual ==
          """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             |
             |SELECT DISTINCT ?resource ?resourceClass ?creationDate ?isDeleted ?lastModificationDate ?deleteDate
             |WHERE {
             |  ?resource a ?resourceClass ;
             |    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
             |    knora-base:creationDate ?creationDate ;
             |    knora-base:isDeleted ?isDeleted .
             |  OPTIONAL { ?resource knora-base:lastModificationDate ?lastModificationDate . }
             |  OPTIONAL { ?resource knora-base:deleteDate ?deleteDate . }
             |}""".stripMargin,
        parses(actual),
      )
    },
    test("class, no cutoff: VALUES is the first element of the WHERE block") {
      val actual = build(Some(resourceClass), None)
      assertTrue(
        actual ==
          """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             |
             |SELECT DISTINCT ?resource ?resourceClass ?creationDate ?isDeleted ?lastModificationDate ?deleteDate
             |WHERE {
             |  VALUES ?resourceClass { <http://www.knora.org/ontology/0001/anything#Thing> }
             |  ?resource a ?resourceClass ;
             |    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
             |    knora-base:creationDate ?creationDate ;
             |    knora-base:isDeleted ?isDeleted .
             |  OPTIONAL { ?resource knora-base:lastModificationDate ?lastModificationDate . }
             |  OPTIONAL { ?resource knora-base:deleteDate ?deleteDate . }
             |}""".stripMargin,
        parses(actual),
      )
    },
    test("no class, cutoff: FILTER is the last element, a typed xsd:dateTime with fractional seconds kept") {
      val actual = build(None, Some(cutoff))
      assertTrue(
        actual ==
          """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             |
             |SELECT DISTINCT ?resource ?resourceClass ?creationDate ?isDeleted ?lastModificationDate ?deleteDate
             |WHERE {
             |  ?resource a ?resourceClass ;
             |    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
             |    knora-base:creationDate ?creationDate ;
             |    knora-base:isDeleted ?isDeleted .
             |  OPTIONAL { ?resource knora-base:lastModificationDate ?lastModificationDate . }
             |  OPTIONAL { ?resource knora-base:deleteDate ?deleteDate . }
             |  FILTER(COALESCE(?lastModificationDate, ?creationDate) >= "2026-10-23T08:00:00.123456Z"^^<http://www.w3.org/2001/XMLSchema#dateTime>)
             |}""".stripMargin,
        parses(actual),
      )
    },
    test("class and cutoff: VALUES first, FILTER last") {
      val actual = build(Some(resourceClass), Some(cutoff))
      assertTrue(
        actual ==
          """|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             |
             |SELECT DISTINCT ?resource ?resourceClass ?creationDate ?isDeleted ?lastModificationDate ?deleteDate
             |WHERE {
             |  VALUES ?resourceClass { <http://www.knora.org/ontology/0001/anything#Thing> }
             |  ?resource a ?resourceClass ;
             |    knora-base:attachedToProject <http://rdfh.ch/projects/0001> ;
             |    knora-base:creationDate ?creationDate ;
             |    knora-base:isDeleted ?isDeleted .
             |  OPTIONAL { ?resource knora-base:lastModificationDate ?lastModificationDate . }
             |  OPTIONAL { ?resource knora-base:deleteDate ?deleteDate . }
             |  FILTER(COALESCE(?lastModificationDate, ?creationDate) >= "2026-10-23T08:00:00.123456Z"^^<http://www.w3.org/2001/XMLSchema#dateTime>)
             |}""".stripMargin,
        parses(actual),
      )
    },
  )
}
