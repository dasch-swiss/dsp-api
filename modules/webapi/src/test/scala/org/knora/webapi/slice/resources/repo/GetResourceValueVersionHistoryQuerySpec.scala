/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.junit.runner.RunWith
import zio.test.*

import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.common.ResourceIri

@RunWith(classOf[DspZTestJUnitRunner])
class GetResourceValueVersionHistoryQuerySpec extends ZIOSpecDefault {

  private val resourceIri = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing-with-history")
  private val startDate   = Instant.parse("2018-06-04T00:00:00Z")
  private val endDate     = Instant.parse("2018-06-05T00:00:00Z")

  private val kb  = "http://www.knora.org/ontology/knora-base#"
  private val res = "<http://rdfh.ch/0001/thing-with-history>"

  override def spec: Spec[TestEnvironment, Any] = suite("GetResourceValueVersionHistoryQuery")(
    test("withDeletedResource=false, no dates") {
      val actual = GetResourceValueVersionHistoryQuery.build(
        resourceIri = resourceIri,
        withDeletedResource = false,
        maybeStartDate = None,
        maybeEndDate = None,
      )
      assertTrue(
        actual ==
          s"""PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
             |PREFIX knora-base: <$kb>
             |
             |SELECT DISTINCT ?versionDate ?author
             |WHERE {
             |  $res ?property ?currentValue .
             |  $res knora-base:isDeleted false .
             |  ?property rdfs:subPropertyOf* knora-base:hasValue .
             |  ?currentValue knora-base:previousValue* ?valueObject .
             |  {
             |    ?valueObject knora-base:valueCreationDate ?versionDate .
             |    ?valueObject knora-base:attachedToUser ?author .
             |  } UNION {
             |    ?valueObject knora-base:deleteDate ?versionDate .
             |    ?valueObject knora-base:deletedBy ?author .
             |  }
             |}
             |ORDER BY DESC(?versionDate)""".stripMargin,
      )
    },
    test("withDeletedResource=true adds the resource-deletion branch and drops the isDeleted pattern") {
      val actual = GetResourceValueVersionHistoryQuery.build(
        resourceIri = resourceIri,
        withDeletedResource = true,
        maybeStartDate = None,
        maybeEndDate = None,
      )
      assertTrue(
        actual ==
          s"""PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
             |PREFIX knora-base: <$kb>
             |
             |SELECT DISTINCT ?versionDate ?author
             |WHERE {
             |  $res ?property ?currentValue .
             |  ?property rdfs:subPropertyOf* knora-base:hasValue .
             |  ?currentValue knora-base:previousValue* ?valueObject .
             |  {
             |    ?valueObject knora-base:valueCreationDate ?versionDate .
             |    ?valueObject knora-base:attachedToUser ?author .
             |  } UNION {
             |    ?valueObject knora-base:deleteDate ?versionDate .
             |    ?valueObject knora-base:deletedBy ?author .
             |  }
             |  UNION {
             |    $res knora-base:deleteDate ?versionDate .
             |    $res knora-base:attachedToUser ?author .
             |  }
             |}
             |ORDER BY DESC(?versionDate)""".stripMargin,
      )
    },
    test("withDeletedResource=false, with start and end dates") {
      val actual = GetResourceValueVersionHistoryQuery.build(
        resourceIri = resourceIri,
        withDeletedResource = false,
        maybeStartDate = Some(startDate),
        maybeEndDate = Some(endDate),
      )
      assertTrue(
        actual ==
          s"""PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
             |PREFIX knora-base: <$kb>
             |
             |SELECT DISTINCT ?versionDate ?author
             |WHERE {
             |  $res ?property ?currentValue .
             |  $res knora-base:isDeleted false .
             |  ?property rdfs:subPropertyOf* knora-base:hasValue .
             |  ?currentValue knora-base:previousValue* ?valueObject .
             |  {
             |    ?valueObject knora-base:valueCreationDate ?versionDate .
             |    ?valueObject knora-base:attachedToUser ?author .
             |  } UNION {
             |    ?valueObject knora-base:deleteDate ?versionDate .
             |    ?valueObject knora-base:deletedBy ?author .
             |  }
             |  FILTER(?versionDate >= "2018-06-04T00:00:00Z"^^<http://www.w3.org/2001/XMLSchema#dateTime>)
             |  FILTER(?versionDate < "2018-06-05T00:00:00Z"^^<http://www.w3.org/2001/XMLSchema#dateTime>)
             |}
             |ORDER BY DESC(?versionDate)""".stripMargin,
      )
    },
    test("only a start date emits only the lower-bound FILTER") {
      val actual = GetResourceValueVersionHistoryQuery.build(
        resourceIri = resourceIri,
        withDeletedResource = false,
        maybeStartDate = Some(startDate),
        maybeEndDate = None,
      )
      assertTrue(
        actual.contains(
          """  FILTER(?versionDate >= "2018-06-04T00:00:00Z"^^<http://www.w3.org/2001/XMLSchema#dateTime>)""",
        ),
        !actual.contains("?versionDate <"),
      )
    },
  )
}
