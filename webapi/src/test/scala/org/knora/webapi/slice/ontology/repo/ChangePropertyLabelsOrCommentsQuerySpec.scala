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
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LabelOrComment
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.domain.LanguageCode.*
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ChangePropertyLabelsOrCommentsQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testPropertyIri: PropertyIri =
    PropertyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#hasTestProperty".toSmartIri)

  private val testLinkValuePropertyIri: PropertyIri =
    PropertyIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#hasTestPropertyValue".toSmartIri)

  private val testLastModificationDate: LastModificationDate =
    LastModificationDate.from(Instant.parse("2023-08-01T10:30:00Z"))

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ChangePropertyLabelsOrCommentsQuerySpec")(
    suite("build")(
      test("should produce the correct query when changing labels without link value property") {
        val newLabels = Seq(
          StringLiteralV2.from("Updated Label", EN),
          StringLiteralV2.from("Étiquette mise à jour", FR),
        )

        ChangePropertyLabelsOrCommentsQuery
          .build(testPropertyIri, LabelOrComment.Label, newLabels, None, testLastModificationDate)
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:hasTestProperty rdfs:label ?oldValues . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |anything:hasTestProperty rdfs:label "Updated Label"@en .
                                 |anything:hasTestProperty rdfs:label "Étiquette mise à jour"@fr . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |OPTIONAL { anything:hasTestProperty rdfs:label ?oldValues . } }""".stripMargin,
            )
          }
      },
      test("should produce the correct query when changing labels with link value property") {
        val newLabels = Seq(
          StringLiteralV2.from("Updated Label", EN),
          StringLiteralV2.from("Étiquette mise à jour", FR),
        )

        ChangePropertyLabelsOrCommentsQuery
          .build(
            testPropertyIri,
            LabelOrComment.Label,
            newLabels,
            Some(testLinkValuePropertyIri),
            testLastModificationDate,
          )
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:hasTestProperty rdfs:label ?oldValues .
                                 |anything:hasTestPropertyValue rdfs:label ?oldLinkValueValues . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |anything:hasTestProperty rdfs:label "Updated Label"@en .
                                 |anything:hasTestProperty rdfs:label "Étiquette mise à jour"@fr .
                                 |anything:hasTestPropertyValue rdfs:label "Updated Label"@en .
                                 |anything:hasTestPropertyValue rdfs:label "Étiquette mise à jour"@fr . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |OPTIONAL { anything:hasTestProperty rdfs:label ?oldValues . }
                                 |OPTIONAL { anything:hasTestPropertyValue rdfs:label ?oldLinkValueValues . } }""".stripMargin,
            )
          }
      },
      test("should produce the correct query when changing comments without link value property") {
        val newComments = Seq(
          StringLiteralV2.from("Updated Comment", EN),
          StringLiteralV2.from("Commentaire mis à jour", FR),
        )

        ChangePropertyLabelsOrCommentsQuery
          .build(testPropertyIri, LabelOrComment.Comment, newComments, None, testLastModificationDate)
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:hasTestProperty rdfs:comment ?oldValues . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |anything:hasTestProperty rdfs:comment "Updated Comment"@en .
                                 |anything:hasTestProperty rdfs:comment "Commentaire mis à jour"@fr . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |OPTIONAL { anything:hasTestProperty rdfs:comment ?oldValues . } }""".stripMargin,
            )
          }
      },
      test("should produce the correct query when changing comments with link value property") {
        val newComments = Seq(
          StringLiteralV2.from("Updated Comment", EN),
          StringLiteralV2.from("Commentaire mis à jour", FR),
        )

        ChangePropertyLabelsOrCommentsQuery
          .build(
            testPropertyIri,
            LabelOrComment.Comment,
            newComments,
            Some(testLinkValuePropertyIri),
            testLastModificationDate,
          )
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:hasTestProperty rdfs:comment ?oldValues .
                                 |anything:hasTestPropertyValue rdfs:comment ?oldLinkValueValues . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |anything:hasTestProperty rdfs:comment "Updated Comment"@en .
                                 |anything:hasTestProperty rdfs:comment "Commentaire mis à jour"@fr .
                                 |anything:hasTestPropertyValue rdfs:comment "Updated Comment"@en .
                                 |anything:hasTestPropertyValue rdfs:comment "Commentaire mis à jour"@fr . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |OPTIONAL { anything:hasTestProperty rdfs:comment ?oldValues . }
                                 |OPTIONAL { anything:hasTestPropertyValue rdfs:comment ?oldLinkValueValues . } }""".stripMargin,
            )
          }
      },
      test("should produce correct query with single label") {
        val newLabels = Seq(StringLiteralV2.from("Single Label", EN))

        ChangePropertyLabelsOrCommentsQuery
          .build(testPropertyIri, LabelOrComment.Label, newLabels, None, testLastModificationDate)
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:hasTestProperty rdfs:label ?oldValues . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |anything:hasTestProperty rdfs:label "Single Label"@en . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |OPTIONAL { anything:hasTestProperty rdfs:label ?oldValues . } }""".stripMargin,
            )
          }
      },
      test("should handle labels with special characters") {
        val newLabels = Seq(
          StringLiteralV2.from("Label with \"quotes\"", EN),
          StringLiteralV2.from("Label with 'apostrophes'", EN),
        )

        ChangePropertyLabelsOrCommentsQuery
          .build(testPropertyIri, LabelOrComment.Label, newLabels, None, testLastModificationDate)
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:hasTestProperty rdfs:label ?oldValues . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |anything:hasTestProperty rdfs:label "Label with \"quotes\""@en .
                                 |anything:hasTestProperty rdfs:label "Label with \'apostrophes\'"@en . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |OPTIONAL { anything:hasTestProperty rdfs:label ?oldValues . } }""".stripMargin,
            )
          }
      },
      test("should handle multiple language variants") {
        val multilingualLabels = Seq(
          StringLiteralV2.from("English Label", EN),
          StringLiteralV2.from("Deutsche Beschriftung", DE),
          StringLiteralV2.from("Étiquette française", FR),
          StringLiteralV2.from("Etichetta italiana", IT),
        )

        ChangePropertyLabelsOrCommentsQuery
          .build(testPropertyIri, LabelOrComment.Label, multilingualLabels, None, testLastModificationDate)
          .map { (actual: Update) =>
            assertTrue(
              actual.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX owl: <http://www.w3.org/2002/07/owl#>
                                 |PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
                                 |DELETE { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |anything:hasTestProperty rdfs:label ?oldValues . } }
                                 |INSERT { GRAPH <http://www.knora.org/ontology/0001/anything> { <http://www.knora.org/ontology/0001/anything> knora-base:lastModificationDate "1970-01-01T00:00:00Z"^^xsd:dateTime .
                                 |anything:hasTestProperty rdfs:label "English Label"@en .
                                 |anything:hasTestProperty rdfs:label "Deutsche Beschriftung"@de .
                                 |anything:hasTestProperty rdfs:label "Étiquette française"@fr .
                                 |anything:hasTestProperty rdfs:label "Etichetta italiana"@it . } }
                                 |WHERE { <http://www.knora.org/ontology/0001/anything> a owl:Ontology ;
                                 |    knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |OPTIONAL { anything:hasTestProperty rdfs:label ?oldValues . } }""".stripMargin,
            )
          }
      },
    ),
  )
}
