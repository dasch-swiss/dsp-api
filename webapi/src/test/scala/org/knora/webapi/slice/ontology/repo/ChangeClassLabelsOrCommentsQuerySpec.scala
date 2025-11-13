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
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LabelOrComment
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.ontology.api.LastModificationDate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ChangeClassLabelsOrCommentsQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testClassIri: ResourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#TestClass".toSmartIri)

  private val testLastModificationDate: LastModificationDate =
    LastModificationDate.from(Instant.parse("2023-08-01T10:30:00Z"))

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ChangeClassLabelsOrCommentsQuerySpec")(
    suite("build")(
      test("should produce the correct query when changing labels") {
        val newLabels = Seq(
          StringLiteralV2.from("Updated Label", Some("en")),
          StringLiteralV2.from("Étiquette mise à jour", Some("fr")),
        )

        ChangeClassLabelsOrCommentsQuery
          .build(testClassIri, LabelOrComment.Label, newLabels, testLastModificationDate)
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:TestClass rdfs:label ?oldValues . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |anything:TestClass rdfs:label "Updated Label"@en .
                                 |anything:TestClass rdfs:label "Étiquette mise à jour"@fr . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:TestClass ?p ?o .
                                 |OPTIONAL { anything:TestClass rdfs:label ?oldValues . } }""".stripMargin,
            )
          }
      },
      test("should produce the correct query when changing comments") {
        val newComments = Seq(
          StringLiteralV2.from("Updated Comment", Some("en")),
          StringLiteralV2.from("Commentaire mis à jour", Some("fr")),
        )

        ChangeClassLabelsOrCommentsQuery
          .build(testClassIri, LabelOrComment.Comment, newComments, testLastModificationDate)
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:TestClass rdfs:comment ?oldValues . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |anything:TestClass rdfs:comment "Updated Comment"@en .
                                 |anything:TestClass rdfs:comment "Commentaire mis à jour"@fr . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:TestClass ?p ?o .
                                 |OPTIONAL { anything:TestClass rdfs:comment ?oldValues . } }""".stripMargin,
            )
          }
      },
      test("should produce correct query with single label") {
        val newLabels = Seq(StringLiteralV2.from("Single Label", Some("en")))

        ChangeClassLabelsOrCommentsQuery
          .build(testClassIri, LabelOrComment.Label, newLabels, testLastModificationDate)
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:TestClass rdfs:label ?oldValues . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |anything:TestClass rdfs:label "Single Label"@en . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:TestClass ?p ?o .
                                 |OPTIONAL { anything:TestClass rdfs:label ?oldValues . } }""".stripMargin,
            )
          }
      },
      test("should handle labels with special characters") {
        val newLabels = Seq(
          StringLiteralV2.from("Label with \"quotes\"", Some("en")),
          StringLiteralV2.from("Label with 'apostrophes'", Some("en")),
        )

        ChangeClassLabelsOrCommentsQuery
          .build(testClassIri, LabelOrComment.Label, newLabels, testLastModificationDate)
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:TestClass rdfs:label ?oldValues . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |anything:TestClass rdfs:label "Label with \"quotes\""@en .
                                 |anything:TestClass rdfs:label "Label with \'apostrophes\'"@en . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:TestClass ?p ?o .
                                 |OPTIONAL { anything:TestClass rdfs:label ?oldValues . } }""".stripMargin,
            )
          }
      },
      test("should handle multiple language variants") {
        val multilingualLabels = Seq(
          StringLiteralV2.from("English Label", Some("en")),
          StringLiteralV2.from("Deutsche Beschriftung", Some("de")),
          StringLiteralV2.from("Étiquette française", Some("fr")),
          StringLiteralV2.from("Etichetta italiana", Some("it")),
        )

        ChangeClassLabelsOrCommentsQuery
          .build(testClassIri, LabelOrComment.Label, multilingualLabels, testLastModificationDate)
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:TestClass rdfs:label ?oldValues . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |anything:TestClass rdfs:label "English Label"@en .
                                 |anything:TestClass rdfs:label "Deutsche Beschriftung"@de .
                                 |anything:TestClass rdfs:label "Étiquette française"@fr .
                                 |anything:TestClass rdfs:label "Etichetta italiana"@it . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:TestClass ?p ?o .
                                 |OPTIONAL { anything:TestClass rdfs:label ?oldValues . } }""".stripMargin,
            )
          }
      },
      test("should die when StringLiterals are missing language codes") {
        val invalidLabels = Seq(
          StringLiteralV2.from("Label without language", None),
        )

        ChangeClassLabelsOrCommentsQuery
          .build(testClassIri, LabelOrComment.Label, invalidLabels, testLastModificationDate)
          .exit
          .map(result => assertTrue(result.isFailure))
      },
      test("should die when any StringLiteral is missing a language code") {
        val mixedLabels = Seq(
          StringLiteralV2.from("Valid Label", Some("en")),
          StringLiteralV2.from("Invalid Label", None),
        )

        ChangeClassLabelsOrCommentsQuery
          .build(testClassIri, LabelOrComment.Label, mixedLabels, testLastModificationDate)
          .exit
          .map(result => assertTrue(result.isFailure))
      },
    ),
  )
}
