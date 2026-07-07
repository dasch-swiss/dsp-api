/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.test.*

import java.time.Instant

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.ontology.domain.model.Cardinality

object ReplaceClassCardinalitiesQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testOntologyIri: OntologyIri =
    OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
  private val testClassIri =
    testOntologyIri.makeClass("TestClass").smartIri
  private val testLastModDate =
    Instant.parse("2023-08-01T10:30:00Z")
  private val testCurrentTime =
    Instant.parse("2023-08-02T12:00:00Z")

  private val deleteRestrictionsQuery =
    """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
      |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
      |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
      |PREFIX owl: <http://www.w3.org/2002/07/owl#>
      |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
      |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>
      |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { anything:TestClass rdfs:subClassOf ?restriction .
      |?restriction ?restrictionPred ?restrictionObj . } }
      |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
      |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
      |anything:TestClass a owl:Class .
      |OPTIONAL { anything:TestClass rdfs:subClassOf ?restriction .
      |?restriction a owl:Restriction ;
      |    ?restrictionPred ?restrictionObj .
      |FILTER ( isBLANK( ?restriction ) ) } } }""".stripMargin

  private val insertAndUpdatePrefixes =
    """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
      |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
      |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
      |PREFIX owl: <http://www.w3.org/2002/07/owl#>
      |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
      |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>""".stripMargin

  private val whereClause =
    """WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
      |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
      |anything:TestClass a owl:Class . } }""".stripMargin

  override val spec: Spec[Any, Nothing] = suite("ReplaceClassCardinalitiesQuery")(
    test("should produce correct query with single cardinality with guiOrder") {
      val cardinalities = Map(
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri ->
          OwlCardinality.KnoraCardinalityInfo(cardinality = Cardinality.ExactlyOne, guiOrder = Some(1)),
      )

      val actual = ReplaceClassCardinalitiesQuery.build(
        ontologyIri = testOntologyIri,
        classIri = testClassIri,
        newCardinalities = cardinalities,
        lastModificationDate = testLastModDate,
        currentTime = testCurrentTime,
      )

      val expected = deleteRestrictionsQuery + ";\n" + insertAndUpdatePrefixes +
        """
          |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
          |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-02T12:00:00Z"^^xsd:dateTime .
          |anything:TestClass rdfs:subClassOf _:node1 .
          |_:node1 a owl:Restriction .
          |_:node1 owl:onProperty anything:hasText .
          |_:node1 owl:cardinality "1"^^xsd:nonNegativeInteger .
          |_:node1 salsah-gui:guiOrder "1"^^xsd:nonNegativeInteger . } }
          |""".stripMargin + whereClause

      assertTrue(actual.sparql == expected)
    },
    test("should produce correct query with multiple cardinalities") {
      val cardinalities = Map(
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri ->
          OwlCardinality.KnoraCardinalityInfo(cardinality = Cardinality.ExactlyOne, guiOrder = Some(1)),
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri ->
          OwlCardinality.KnoraCardinalityInfo(cardinality = Cardinality.ZeroOrOne),
      )

      val actual = ReplaceClassCardinalitiesQuery.build(
        ontologyIri = testOntologyIri,
        classIri = testClassIri,
        newCardinalities = cardinalities,
        lastModificationDate = testLastModDate,
        currentTime = testCurrentTime,
      )

      val expected = deleteRestrictionsQuery + ";\n" + insertAndUpdatePrefixes +
        """
          |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
          |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-02T12:00:00Z"^^xsd:dateTime .
          |anything:TestClass rdfs:subClassOf _:node1 .
          |_:node1 a owl:Restriction .
          |_:node1 owl:onProperty anything:hasText .
          |_:node1 owl:cardinality "1"^^xsd:nonNegativeInteger .
          |_:node1 salsah-gui:guiOrder "1"^^xsd:nonNegativeInteger .
          |anything:TestClass rdfs:subClassOf _:node2 .
          |_:node2 a owl:Restriction .
          |_:node2 owl:onProperty anything:hasInteger .
          |_:node2 owl:maxCardinality "1"^^xsd:nonNegativeInteger . } }
          |""".stripMargin + whereClause

      assertTrue(actual.sparql == expected)
    },
    test("should produce correct query with empty cardinalities") {
      val actual = ReplaceClassCardinalitiesQuery.build(
        ontologyIri = testOntologyIri,
        classIri = testClassIri,
        newCardinalities = Map.empty,
        lastModificationDate = testLastModDate,
        currentTime = testCurrentTime,
      )

      val expected = deleteRestrictionsQuery + ";\n" + insertAndUpdatePrefixes +
        """
          |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
          |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-02T12:00:00Z"^^xsd:dateTime . } }
          |""".stripMargin + whereClause

      assertTrue(actual.sparql == expected)
    },
  )
}
