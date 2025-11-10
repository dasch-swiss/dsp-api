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
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.PropertyInfoContentV2
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object CreatePropertyQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testOntologyIri: OntologyIri =
    OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
  private val testPropertyIri: SmartIri =
    testOntologyIri.makeProperty("hasTestProperty").smartIri
  private val testLastModificationDate: Instant =
    Instant.parse("2023-08-01T10:30:00Z")

  val testPropertyDef = PropertyInfoContentV2(
    propertyIri = testPropertyIri,
    predicates = Map(
      "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri ->
        PredicateInfoV2(
          predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
          objects = Seq(StringLiteralV2.from("""Test "Property"""", Some("en"))),
        ),
      "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri ->
        PredicateInfoV2(
          predicateIri = "http://www.w3.org/2000/01/rdf-schema#comment".toSmartIri,
          objects = Seq(StringLiteralV2.from("""A test "property"""", Some("en"))),
        ),
      "http://www.knora.org/ontology/knora-base#objectType".toSmartIri ->
        PredicateInfoV2(
          predicateIri = "http://www.knora.org/ontology/knora-base#objectType".toSmartIri,
          objects = Seq(SmartIriLiteralV2("http://www.knora.org/ontology/knora-base#TextValue".toSmartIri)),
        ),
      "http://www.knora.org/ontology/knora-base#subjectType".toSmartIri ->
        PredicateInfoV2(
          predicateIri = "http://www.knora.org/ontology/knora-base#subjectType".toSmartIri,
          objects = Seq(SmartIriLiteralV2("http://www.knora.org/ontology/knora-base#Resource".toSmartIri)),
        ),
    ),
    subPropertyOf = Set("http://www.knora.org/ontology/knora-base#hasValue".toSmartIri),
    ontologySchema = InternalSchema,
  )

  val testLinkValuePropertyDef = Some(
    PropertyInfoContentV2(
      propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasTestPropertyValue".toSmartIri,
      predicates = Map(
        "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri ->
          PredicateInfoV2(
            predicateIri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
            objects = Seq(StringLiteralV2.from("Test Property Value", Some("en"))),
          ),
      ),
      subPropertyOf = Set("http://www.knora.org/ontology/knora-base#hasLinkToValue".toSmartIri),
      ontologySchema = InternalSchema,
    ),
  )

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("CreatePropertyFactorySpec")(
    test("should produce the correct query with a link value property") {
      CreatePropertyQuery
        .build(testPropertyDef, testLinkValuePropertyDef, testLastModificationDate)
        .map((actual: Update) =>
          assertTrue(
            actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                               |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                               |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
                               |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                               |<http://www.knora.org/ontology/0001/anything#hasTestProperty> rdfs:label "Test \"Property\""@en .
                               |<http://www.knora.org/ontology/0001/anything#hasTestProperty> rdfs:comment "A test \"property\""@en .
                               |<http://www.knora.org/ontology/0001/anything#hasTestProperty> knora-base:objectType knora-base:TextValue .
                               |<http://www.knora.org/ontology/0001/anything#hasTestProperty> knora-base:subjectType knora-base:Resource .
                               |<http://www.knora.org/ontology/0001/anything#hasTestProperty> rdfs:subPropertyOf knora-base:hasValue .
                               |<http://www.knora.org/ontology/0001/anything#hasTestPropertyValue> rdfs:label "Test Property Value"@en .
                               |<http://www.knora.org/ontology/0001/anything#hasTestPropertyValue> rdfs:subPropertyOf knora-base:hasLinkToValue . } }
                               |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                               |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . }
                               |FILTER NOT EXISTS { <http://www.knora.org/ontology/0001/anything#hasTestProperty> rdf:type ?existingPropertyType . }
                               |FILTER NOT EXISTS { <http://www.knora.org/ontology/0001/anything#hasTestPropertyValue> a ?existingLinkValuePropertyType . } }""".stripMargin,
          ),
        )
    },
    test("should produce correct query without link value property") {
      CreatePropertyQuery
        .build(testPropertyDef, None, testLastModificationDate)
        .map((actual: Update) =>
          assertTrue(
            actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                               |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                               |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                               |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                               |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                               |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
                               |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                               |<http://www.knora.org/ontology/0001/anything#hasTestProperty> rdfs:label "Test \"Property\""@en .
                               |<http://www.knora.org/ontology/0001/anything#hasTestProperty> rdfs:comment "A test \"property\""@en .
                               |<http://www.knora.org/ontology/0001/anything#hasTestProperty> knora-base:objectType knora-base:TextValue .
                               |<http://www.knora.org/ontology/0001/anything#hasTestProperty> knora-base:subjectType knora-base:Resource .
                               |<http://www.knora.org/ontology/0001/anything#hasTestProperty> rdfs:subPropertyOf knora-base:hasValue . } }
                               |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                               |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . }
                               |FILTER NOT EXISTS { <http://www.knora.org/ontology/0001/anything#hasTestProperty> rdf:type ?existingPropertyType . } }""".stripMargin,
          ),
        )
    },
  )
}
