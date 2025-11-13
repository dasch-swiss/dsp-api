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
import org.knora.webapi.slice.ontology.api.LastModificationDate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object DeleteOntologyCommentQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testOntologyIri: OntologyIri =
    OntologyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything".toSmartIri)

  private val testLastModificationDate: LastModificationDate =
    LastModificationDate.from(Instant.parse("2023-08-01T10:30:00Z"))

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("DeleteOntologyCommentQuerySpec")(
    suite("build")(
      test("should produce the correct query for deleting an ontology comment") {
        DeleteOntologyCommentQuery
          .build(testOntologyIri, testLastModificationDate)
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> rdfs:comment ?oldComment .
                                 |<http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:comment ?oldComment . }""".stripMargin,
            )
          }
      },
    ),
  )
}
