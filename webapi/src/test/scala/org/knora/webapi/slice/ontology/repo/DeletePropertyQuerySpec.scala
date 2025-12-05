/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.*
import zio.test.*

import java.time.Instant

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.ontology.api.LastModificationDate

object DeletePropertyQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testOntologyIri: OntologyIri =
    OntologyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)
  private val testPropertyIri: PropertyIri =
    testOntologyIri.makeProperty("hasTestProperty")
  private val testLinkValuePropertyIri: PropertyIri =
    testOntologyIri.makeProperty("hasTestPropertyValue")
  private val testLastModificationDate: LastModificationDate =
    LastModificationDate.from(Instant.parse("2023-08-01T10:30:00Z"))

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("DeletePropertyQuerySpec")(
    test("should produce the correct query with a link value property") {
      DeletePropertyQuery
        .build(testPropertyIri, Some(testLinkValuePropertyIri), testLastModificationDate)
        .map { case (_, query) =>
          val queryString = query.getQueryString
          assertTrue(queryString ==
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
              |PREFIX owl: <http://www.w3.org/2002/07/owl#>
              |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
              |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
              |anything:hasTestProperty ?propertyPred ?propertyObj .
              |anything:hasTestPropertyValue ?linkValuePropertyObj ?linkValuePropertyPred . } }
              |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime . } }
              |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
              |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
              |anything:hasTestProperty a owl:ObjectProperty ;
              |    ?propertyPred ?propertyObj .
              |FILTER NOT EXISTS { ?s ?p anything:hasTestProperty . }
              |anything:hasTestPropertyValue ?linkValuePropertyObj ?linkValuePropertyPred . }""".stripMargin)
        }
    },
    test("should produce correct query without link value property") {
      DeletePropertyQuery
        .build(testPropertyIri, None, testLastModificationDate)
        .map { case (_, query) =>
          val queryString = query.getQueryString
          assertTrue(queryString ==
            """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
              |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
              |PREFIX owl: <http://www.w3.org/2002/07/owl#>
              |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
              |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
              |anything:hasTestProperty ?propertyPred ?propertyObj . } }
              |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime . } }
              |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
              |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
              |anything:hasTestProperty a owl:ObjectProperty ;
              |    ?propertyPred ?propertyObj .
              |FILTER NOT EXISTS { ?s ?p anything:hasTestProperty . } }""".stripMargin)
        }
    },
  )
}
