/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.*
import zio.test.*

import java.time.Instant

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.PropertyIri

object DeletePropertyCommentsQuerySpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testPropertyIri = PropertyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#hasName".toSmartIri)
  private val testLmd         = LastModificationDate.from(Instant.parse("2024-01-15T10:30:00Z"))

  override def spec: Spec[TestEnvironment, Any] = suite("DeletePropertyCommentsSpec")(
    test("should produce correct query for property without linkValueProperty") {
      for {
        query   <- DeletePropertyCommentsQuery.build(testPropertyIri, None, testLmd)
        actual   = query.getQueryString
        instant <- Clock.instant
      } yield assertTrue(
        actual ==
          s"""PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
             |PREFIX owl: <http://www.w3.org/2002/07/owl#>
             |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
             |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
             |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { anything:hasName rdfs:comment ?comments .
             |<http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime . } }
             |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "$instant"^^xsd:dateTime . } }
             |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
             |    knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
             |anything:hasName rdfs:comment ?comments . } }""".stripMargin,
      )
    },
    test("should produce correct query for property with linkValueProperty") {
      val testLinkValuePropertyIri =
        PropertyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#hasLinkValue".toSmartIri)

      for {
        query   <- DeletePropertyCommentsQuery.build(testPropertyIri, Some(testLinkValuePropertyIri), testLmd)
        actual   = query.getQueryString
        instant <- Clock.instant
      } yield assertTrue(
        actual ==
          s"""PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
             |PREFIX owl: <http://www.w3.org/2002/07/owl#>
             |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
             |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
             |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { anything:hasName rdfs:comment ?comments .
             |<http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
             |anything:hasLinkValue rdfs:comment ?comments . } }
             |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "$instant"^^xsd:dateTime . } }
             |WHERE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
             |    knora-base:lastModificationDate "2024-01-15T10:30:00Z"^^xsd:dateTime .
             |anything:hasName rdfs:comment ?comments . } }""".stripMargin,
      )
    },
    test("should produce correct query for different ontology") {
      val differentPropertyIri =
        PropertyIri.unsafeFrom("http://www.knora.org/ontology/0803/incunabula#title".toSmartIri)
      val differentLmd = LastModificationDate.from(Instant.parse("2024-06-20T14:45:30Z"))

      for {
        query   <- DeletePropertyCommentsQuery.build(differentPropertyIri, None, differentLmd)
        actual   = query.getQueryString
        instant <- Clock.instant
      } yield assertTrue(
        actual ==
          s"""PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
             |PREFIX owl: <http://www.w3.org/2002/07/owl#>
             |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
             |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             |PREFIX incunabula: <http://www.knora.org/ontology/0803/incunabula#>
             |DELETE { GRAPH <http://www.knora.org/ontology/0803/incunabula> { incunabula:title rdfs:comment ?comments .
             |<http://www.knora.org/ontology/0803/incunabula> knora-base:lastModificationDate "2024-06-20T14:45:30Z"^^xsd:dateTime . } }
             |INSERT { GRAPH <http://www.knora.org/ontology/0803/incunabula> { <http://www.knora.org/ontology/0803/incunabula> knora-base:lastModificationDate "$instant"^^xsd:dateTime . } }
             |WHERE { GRAPH <http://www.knora.org/ontology/0803/incunabula> { <http://www.knora.org/ontology/0803/incunabula> a owl:Ontology ;
             |    knora-base:lastModificationDate "2024-06-20T14:45:30Z"^^xsd:dateTime .
             |incunabula:title rdfs:comment ?comments . } }""".stripMargin,
      )
    },
  )
}
