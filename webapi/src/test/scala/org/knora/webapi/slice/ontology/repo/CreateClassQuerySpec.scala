/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.*
import zio.test.*

import java.time.Instant

import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object CreateClassQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testOntologyIri: OntologyIri =
    OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
  private val testClassIri: SmartIri =
    testOntologyIri.makeClass("TestClass").smartIri
  private val testLastModificationDate: Instant =
    Instant.parse("2023-08-01T10:30:00Z")

  private val testClassDefWithCardinalities = ClassInfoContentV2(
    classIri = testClassIri,
    predicates = Map(
      "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri ->
        PredicateInfoV2(
          predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
          objects = Seq(StringLiteralV2.from("""Test "Class"""", Some("en"))),
        ),
      "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri ->
        PredicateInfoV2(
          predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
          objects = Seq(StringLiteralV2.from("""A test "class"""", Some("en"))),
        ),
    ),
    subClassOf = Set("http://www.knora.org/ontology/knora-base#Resource".toSmartIri),
    directCardinalities = Map(
      "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri ->
        OwlCardinality.KnoraCardinalityInfo(cardinality = Cardinality.ExactlyOne, guiOrder = Some(1)),
      "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri ->
        OwlCardinality.KnoraCardinalityInfo(cardinality = Cardinality.ZeroOrOne),
    ),
    ontologySchema = InternalSchema,
  )

  private val testClassDefWithoutCardinalities = ClassInfoContentV2(
    classIri = testClassIri,
    predicates = Map(
      "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri ->
        PredicateInfoV2(
          predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
          objects = Seq(StringLiteralV2.from("Simple Test Class", Some("en"))),
        ),
    ),
    subClassOf = Set("http://www.knora.org/ontology/knora-base#Resource".toSmartIri),
    directCardinalities = Map.empty,
    ontologySchema = InternalSchema,
  )

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("CreateClassQuerySpec")(
    test("should produce the correct query with cardinalities") {
      CreateClassQuery
        .build(testClassDefWithCardinalities, testLastModificationDate)
        .map((actual: Update) =>
          assertTrue(
            actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                               |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                               |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                               |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>
                               |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
                               |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                               |anything:TestClass rdfs:subClassOf knora-base:Resource .
                               |anything:TestClass rdfs:label "Test \"Class\""@en .
                               |anything:TestClass rdfs:comment "A test \"class\""@en .
                               |anything:TestClass rdfs:subClassOf _:node1 .
                               |_:node1 a owl:Restriction .
                               |_:node1 owl:onProperty anything:hasText .
                               |_:node1 owl:cardinality "1"^^xsd:nonNegativeInteger .
                               |_:node1 salsah-gui:guiOrder "1"^^xsd:nonNegativeInteger .
                               |anything:TestClass rdfs:subClassOf _:node2 .
                               |_:node2 a owl:Restriction .
                               |_:node2 owl:onProperty anything:hasInteger .
                               |_:node2 owl:maxCardinality "1"^^xsd:nonNegativeInteger . } }
                               |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                               |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . }
                               |FILTER NOT EXISTS { anything:TestClass a ?existingClassType . } }""".stripMargin,
          ),
        )
    },
    test("should produce correct query without cardinalities") {
      CreateClassQuery
        .build(testClassDefWithoutCardinalities, testLastModificationDate)
        .map((actual: Update) =>
          assertTrue(
            actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                               |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                               |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                               |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>
                               |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
                               |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                               |anything:TestClass rdfs:subClassOf knora-base:Resource .
                               |anything:TestClass rdfs:label "Simple Test Class"@en . } }
                               |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                               |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . }
                               |FILTER NOT EXISTS { anything:TestClass a ?existingClassType . } }""".stripMargin,
          ),
        )
    },
  )
}
