/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

import java.time.Instant

object GetResourceValueVersionHistoryQuerySpec extends ZIOSpecDefault {

  private val resourceIri = "http://rdfh.ch/0001/thing-with-history"
  private val startDate   = Instant.parse("2018-06-04T00:00:00Z")
  private val endDate     = Instant.parse("2018-06-05T00:00:00Z")

  override def spec: Spec[TestEnvironment, Any] = suite("GetResourceValueVersionHistoryQuery")(
    test("withDeletedResource=false, no dates") {
      val actual = GetResourceValueVersionHistoryQuery
        .build(
          resourceIri = resourceIri,
          withDeletedResource = false,
          maybeStartDate = None,
          maybeEndDate = None,
        )
        .getQueryString
      assertTrue(
        actual ==
          """PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |SELECT DISTINCT ?versionDate ?author
            |WHERE { <http://rdfh.ch/0001/thing-with-history> ?property ?currentValue .
            |<http://rdfh.ch/0001/thing-with-history> knora-base:isDeleted false .
            |?property rdfs:subPropertyOf* knora-base:hasValue .
            |?currentValue knora-base:previousValue* ?valueObject .
            |{ ?valueObject knora-base:valueCreationDate ?versionDate .
            |?valueObject knora-base:attachedToUser ?author . } UNION { ?valueObject knora-base:deleteDate ?versionDate .
            |?valueObject knora-base:deletedBy ?author . } }
            |ORDER BY DESC( ?versionDate )
            |""".stripMargin,
      )
    },
    test("withDeletedResource=true, no dates") {
      val actual = GetResourceValueVersionHistoryQuery
        .build(
          resourceIri = resourceIri,
          withDeletedResource = true,
          maybeStartDate = None,
          maybeEndDate = None,
        )
        .getQueryString
      assertTrue(
        actual ==
          """PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |SELECT DISTINCT ?versionDate ?author
            |WHERE { <http://rdfh.ch/0001/thing-with-history> ?property ?currentValue .
            |?property rdfs:subPropertyOf* knora-base:hasValue .
            |?currentValue knora-base:previousValue* ?valueObject .
            |{ ?valueObject knora-base:valueCreationDate ?versionDate .
            |?valueObject knora-base:attachedToUser ?author . } UNION { ?valueObject knora-base:deleteDate ?versionDate .
            |?valueObject knora-base:deletedBy ?author . } UNION { <http://rdfh.ch/0001/thing-with-history> knora-base:deleteDate ?versionDate .
            |<http://rdfh.ch/0001/thing-with-history> knora-base:attachedToUser ?author . } }
            |ORDER BY DESC( ?versionDate )
            |""".stripMargin,
      )
    },
    test("withDeletedResource=false, with start and end dates") {
      val actual = GetResourceValueVersionHistoryQuery
        .build(
          resourceIri = resourceIri,
          withDeletedResource = false,
          maybeStartDate = Some(startDate),
          maybeEndDate = Some(endDate),
        )
        .getQueryString
      assertTrue(
        actual ==
          """PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |SELECT DISTINCT ?versionDate ?author
            |WHERE { <http://rdfh.ch/0001/thing-with-history> ?property ?currentValue .
            |<http://rdfh.ch/0001/thing-with-history> knora-base:isDeleted false .
            |?property rdfs:subPropertyOf* knora-base:hasValue .
            |?currentValue knora-base:previousValue* ?valueObject .
            |{ ?valueObject knora-base:valueCreationDate ?versionDate .
            |?valueObject knora-base:attachedToUser ?author . } UNION { ?valueObject knora-base:deleteDate ?versionDate .
            |?valueObject knora-base:deletedBy ?author . }
            |FILTER ( ?versionDate >= "2018-06-04T00:00:00Z"^^xsd:dateTime )
            |FILTER ( ?versionDate < "2018-06-05T00:00:00Z"^^xsd:dateTime ) }
            |ORDER BY DESC( ?versionDate )
            |""".stripMargin,
      )
    },
    test("withDeletedResource=true, with start and end dates") {
      val actual = GetResourceValueVersionHistoryQuery
        .build(
          resourceIri = resourceIri,
          withDeletedResource = true,
          maybeStartDate = Some(startDate),
          maybeEndDate = Some(endDate),
        )
        .getQueryString
      assertTrue(
        actual ==
          """PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
            |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |SELECT DISTINCT ?versionDate ?author
            |WHERE { <http://rdfh.ch/0001/thing-with-history> ?property ?currentValue .
            |?property rdfs:subPropertyOf* knora-base:hasValue .
            |?currentValue knora-base:previousValue* ?valueObject .
            |{ ?valueObject knora-base:valueCreationDate ?versionDate .
            |?valueObject knora-base:attachedToUser ?author . } UNION { ?valueObject knora-base:deleteDate ?versionDate .
            |?valueObject knora-base:deletedBy ?author . } UNION { <http://rdfh.ch/0001/thing-with-history> knora-base:deleteDate ?versionDate .
            |<http://rdfh.ch/0001/thing-with-history> knora-base:attachedToUser ?author . }
            |FILTER ( ?versionDate >= "2018-06-04T00:00:00Z"^^xsd:dateTime )
            |FILTER ( ?versionDate < "2018-06-05T00:00:00Z"^^xsd:dateTime ) }
            |ORDER BY DESC( ?versionDate )
            |""".stripMargin,
      )
    },
  )
}
