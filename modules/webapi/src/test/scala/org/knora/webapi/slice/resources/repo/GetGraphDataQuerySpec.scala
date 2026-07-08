/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter

object GetGraphDataQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testStartNodeIri   = "http://rdfh.ch/0001/start"
  private val testExcludePropIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri
  private val testLimit          = 50

  override def spec: Spec[TestEnvironment, Any] = suite("GetGraphDataQuerySpec")(
    suite("buildStartNodeOnly")(
      test("should produce correct query for start node") {
        val actual = GetGraphDataQuery.buildStartNodeOnly(testStartNodeIri, testLimit).getQueryString
        assertTrue(
          actual ==
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |SELECT ?node ?nodeClass ?nodeLabel ?nodeCreator ?nodeProject ?nodePermissions ?linkValue ?linkProp ?linkValueCreator ?linkValuePermissions
              |WHERE { ?node a ?nodeClass ;
              |    rdfs:label ?nodeLabel ;
              |    knora-base:attachedToUser ?nodeCreator ;
              |    knora-base:attachedToProject ?nodeProject ;
              |    knora-base:hasPermissions ?nodePermissions .
              |FILTER NOT EXISTS { ?node knora-base:isDeleted true . }
              |FILTER ( ?node = <http://rdfh.ch/0001/start> ) }
              |LIMIT 50
              |""".stripMargin,
        )
      },
    ),
    suite("buildTraversal")(
      test("should produce correct query for outbound traversal without exclude property") {
        val actual =
          GetGraphDataQuery.buildTraversal(testStartNodeIri, outbound = true, None, testLimit).getQueryString
        assertTrue(
          actual ==
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |SELECT ?node ?nodeClass ?nodeLabel ?nodeCreator ?nodeProject ?nodePermissions ?linkValue ?linkProp ?linkValueCreator ?linkValuePermissions
              |WHERE { ?linkProp rdfs:subPropertyOf* knora-base:hasLinkTo .
              |<http://rdfh.ch/0001/start> ?linkProp ?node .
              |FILTER NOT EXISTS { ?node knora-base:isDeleted true . }
              |?linkValue a knora-base:LinkValue ;
              |    rdf:subject <http://rdfh.ch/0001/start> ;
              |    rdf:predicate ?linkProp ;
              |    rdf:object ?node .
              |?node a ?nodeClass ;
              |    rdfs:label ?nodeLabel ;
              |    knora-base:attachedToUser ?nodeCreator ;
              |    knora-base:attachedToProject ?nodeProject ;
              |    knora-base:hasPermissions ?nodePermissions .
              |?linkValue knora-base:attachedToUser ?linkValueCreator ;
              |    knora-base:hasPermissions ?linkValuePermissions . }
              |LIMIT 50
              |""".stripMargin,
        )
      },
      test("should produce correct query for inbound traversal without exclude property") {
        val actual =
          GetGraphDataQuery.buildTraversal(testStartNodeIri, outbound = false, None, testLimit).getQueryString
        assertTrue(
          actual ==
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |SELECT ?node ?nodeClass ?nodeLabel ?nodeCreator ?nodeProject ?nodePermissions ?linkValue ?linkProp ?linkValueCreator ?linkValuePermissions
              |WHERE { ?linkProp rdfs:subPropertyOf* knora-base:hasLinkTo .
              |?node ?linkProp <http://rdfh.ch/0001/start> .
              |FILTER NOT EXISTS { ?node knora-base:isDeleted true . }
              |?linkValue a knora-base:LinkValue ;
              |    rdf:subject ?node ;
              |    rdf:predicate ?linkProp ;
              |    rdf:object <http://rdfh.ch/0001/start> .
              |?node a ?nodeClass ;
              |    rdfs:label ?nodeLabel ;
              |    knora-base:attachedToUser ?nodeCreator ;
              |    knora-base:attachedToProject ?nodeProject ;
              |    knora-base:hasPermissions ?nodePermissions .
              |?linkValue knora-base:attachedToUser ?linkValueCreator ;
              |    knora-base:hasPermissions ?linkValuePermissions . }
              |LIMIT 50
              |""".stripMargin,
        )
      },
      test("should produce correct query for outbound traversal with exclude property") {
        val actual =
          GetGraphDataQuery
            .buildTraversal(testStartNodeIri, outbound = true, Some(testExcludePropIri), testLimit)
            .getQueryString
        assertTrue(
          actual ==
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |SELECT ?node ?nodeClass ?nodeLabel ?nodeCreator ?nodeProject ?nodePermissions ?linkValue ?linkProp ?linkValueCreator ?linkValuePermissions
              |WHERE { ?linkProp rdfs:subPropertyOf* knora-base:hasLinkTo .
              |<http://rdfh.ch/0001/start> ?linkProp ?node .
              |FILTER NOT EXISTS { ?excludedProp rdfs:subPropertyOf* <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
              |<http://rdfh.ch/0001/start> ?excludedProp ?node . }
              |FILTER NOT EXISTS { ?node knora-base:isDeleted true . }
              |?linkValue a knora-base:LinkValue ;
              |    rdf:subject <http://rdfh.ch/0001/start> ;
              |    rdf:predicate ?linkProp ;
              |    rdf:object ?node .
              |?node a ?nodeClass ;
              |    rdfs:label ?nodeLabel ;
              |    knora-base:attachedToUser ?nodeCreator ;
              |    knora-base:attachedToProject ?nodeProject ;
              |    knora-base:hasPermissions ?nodePermissions .
              |?linkValue knora-base:attachedToUser ?linkValueCreator ;
              |    knora-base:hasPermissions ?linkValuePermissions . }
              |LIMIT 50
              |""".stripMargin,
        )
      },
      test("should produce correct query for inbound traversal with exclude property") {
        val actual =
          GetGraphDataQuery
            .buildTraversal(testStartNodeIri, outbound = false, Some(testExcludePropIri), testLimit)
            .getQueryString
        assertTrue(
          actual ==
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
              |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
              |SELECT ?node ?nodeClass ?nodeLabel ?nodeCreator ?nodeProject ?nodePermissions ?linkValue ?linkProp ?linkValueCreator ?linkValuePermissions
              |WHERE { ?linkProp rdfs:subPropertyOf* knora-base:hasLinkTo .
              |?node ?linkProp <http://rdfh.ch/0001/start> .
              |FILTER NOT EXISTS { ?excludedProp rdfs:subPropertyOf* <http://www.knora.org/ontology/0001/anything#hasOtherThing> .
              |?node ?excludedProp <http://rdfh.ch/0001/start> . }
              |FILTER NOT EXISTS { ?node knora-base:isDeleted true . }
              |?linkValue a knora-base:LinkValue ;
              |    rdf:subject ?node ;
              |    rdf:predicate ?linkProp ;
              |    rdf:object <http://rdfh.ch/0001/start> .
              |?node a ?nodeClass ;
              |    rdfs:label ?nodeLabel ;
              |    knora-base:attachedToUser ?nodeCreator ;
              |    knora-base:attachedToProject ?nodeProject ;
              |    knora-base:hasPermissions ?nodePermissions .
              |?linkValue knora-base:attachedToUser ?linkValueCreator ;
              |    knora-base:hasPermissions ?linkValuePermissions . }
              |LIMIT 50
              |""".stripMargin,
        )
      },
    ),
  )
}
