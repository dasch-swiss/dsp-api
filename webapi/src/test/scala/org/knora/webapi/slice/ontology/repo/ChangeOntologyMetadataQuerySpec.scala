/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import eu.timepit.refined.types.string.NonEmptyString
import zio.*
import zio.test.*

import java.time.Instant
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.ontology.api.LastModificationDate
import org.knora.webapi.slice.ontology.repo.ChangeOntologyMetadataQuery
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ChangeOntologyMetadataQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testOntologyIri: OntologyIri =
    OntologyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything".toSmartIri)

  private val testLastModificationDate: LastModificationDate =
    LastModificationDate.from(Instant.parse("2023-08-01T10:30:00Z"))

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ChangeOntologyMetadataQuerySpec")(
    suite("build")(
      test("should produce the correct query when changing only the label") {
        ChangeOntologyMetadataQuery
          .build(
            testOntologyIri,
            newLabel = Some("Updated Ontology Label"),
            hasOldComment = false,
            deleteOldComment = false,
            newComment = None,
            testLastModificationDate,
          )
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> rdfs:label ?oldLabel .
                                 |<http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:label "Updated Ontology Label"^^xsd:string . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:label ?oldLabel . }""".stripMargin,
            )
          }
      },
      test("should produce the correct query when changing only the comment") {
        ChangeOntologyMetadataQuery
          .build(
            testOntologyIri,
            newLabel = None,
            hasOldComment = true,
            deleteOldComment = false,
            newComment = Some(NonEmptyString.unsafeFrom("Updated ontology comment")),
            testLastModificationDate,
          )
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> rdfs:comment ?oldComment .
                                 |<http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:comment "Updated ontology comment"^^xsd:string . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:comment ?oldComment . }""".stripMargin,
            )
          }
      },
      test("should produce the correct query when changing both label and comment") {
        ChangeOntologyMetadataQuery
          .build(
            testOntologyIri,
            newLabel = Some("New Label"),
            hasOldComment = true,
            deleteOldComment = false,
            newComment = Some(NonEmptyString.unsafeFrom("New comment")),
            testLastModificationDate,
          )
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> rdfs:label ?oldLabel .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:comment ?oldComment .
                                 |<http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:label "New Label"^^xsd:string .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:comment "New comment"^^xsd:string . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:label ?oldLabel .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:comment ?oldComment . }""".stripMargin,
            )
          }
      },
      test("should produce the correct query when deleting a comment without adding a new one") {
        ChangeOntologyMetadataQuery
          .build(
            testOntologyIri,
            newLabel = None,
            hasOldComment = true,
            deleteOldComment = true,
            newComment = None,
            testLastModificationDate,
          )
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
      test("should produce the correct query when updating only lastModificationDate (no label or comment changes)") {
        ChangeOntologyMetadataQuery
          .build(
            testOntologyIri,
            newLabel = None,
            hasOldComment = false,
            deleteOldComment = false,
            newComment = None,
            testLastModificationDate,
          )
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . }""".stripMargin,
            )
          }
      },
      test("should handle special characters in labels") {
        ChangeOntologyMetadataQuery
          .build(
            testOntologyIri,
            newLabel = Some("Label with \"quotes\" and 'apostrophes'"),
            hasOldComment = false,
            deleteOldComment = false,
            newComment = None,
            testLastModificationDate,
          )
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> rdfs:label ?oldLabel .
                                 |<http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:label "Label with \"quotes\" and \'apostrophes\'"^^xsd:string . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:label ?oldLabel . }""".stripMargin,
            )
          }
      },
      test("should handle special characters in comments") {
        ChangeOntologyMetadataQuery
          .build(
            testOntologyIri,
            newLabel = None,
            hasOldComment = true,
            deleteOldComment = false,
            newComment = Some(NonEmptyString.unsafeFrom("Comment with \"quotes\" and newlines\nand tabs\t")),
            testLastModificationDate,
          )
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> rdfs:comment ?oldComment .
                                 |<http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:comment "Comment with \"quotes\" and newlines\nand tabs\t"^^xsd:string . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:comment ?oldComment . }""".stripMargin,
            )
          }
      },
      test("should not delete comment when hasOldComment is false") {
        ChangeOntologyMetadataQuery
          .build(
            testOntologyIri,
            newLabel = Some("New Label"),
            hasOldComment = false,
            deleteOldComment = true,
            newComment = None,
            testLastModificationDate,
          )
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> rdfs:label ?oldLabel .
                                 |<http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:label "New Label"^^xsd:string . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |<http://www.knora.org/ontology/0001/anything> rdfs:label ?oldLabel . }""".stripMargin,
            )
          }
      },
    ),
  )
}