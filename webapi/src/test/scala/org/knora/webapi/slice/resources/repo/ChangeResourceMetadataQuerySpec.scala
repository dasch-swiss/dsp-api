/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.*
import zio.NonEmptyChunk
import zio.test.*

import java.time.Instant

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.ontology.api.LastModificationDate
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ChangeResourceMetadataQuerySpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val testProject = KnoraProject(
    ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
    Shortname.unsafeFrom("anything"),
    Shortcode.unsafeFrom("0001"),
    None,
    NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.from("Test project"))),
    List.empty,
    None,
    Status.Active,
    SelfJoin.CannotJoin,
    RestrictedView.default,
    Set.empty,
    Set.empty,
  )

  private val testResourceIri      = ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing".toSmartIri)
  private val testResourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#Thing".toSmartIri)
  private val testLastModificationDate = LastModificationDate.from(Instant.parse("2023-08-01T10:30:00Z"))
  private val testNewModificationDate  = LastModificationDate.from(Instant.parse("2023-08-02T15:45:00Z"))

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ChangeResourceMetadataQuerySpec")(
    suite("build")(
      test("should produce the correct query when changing only the label") {
        ChangeResourceMetadataQuery
          .build(
            project = testProject,
            resourceIri = testResourceIri,
            resourceClassIri = testResourceClassIri,
            maybeLastModificationDate = Some(testLastModificationDate),
            maybeNewModificationDate = Some(testNewModificationDate),
            maybeLabel = Some("Updated Resource Label"),
            maybePermissions = None,
          )
          .map { case (lmd, update) =>
            assertTrue(
              lmd == testNewModificationDate,
              update.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                 |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |<http://rdfh.ch/0001/a-thing> rdfs:label ?oldLabel . } }
                                 |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-02T15:45:00Z"^^xsd:dateTime .
                                 |<http://rdfh.ch/0001/a-thing> rdfs:label "Updated Resource Label" . } }
                                 |WHERE { <http://rdfh.ch/0001/a-thing> a <http://www.knora.org/ontology/0001/anything#Thing> .
                                 |<http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |OPTIONAL { <http://rdfh.ch/0001/a-thing> rdfs:label ?oldLabel . } }""".stripMargin,
            )
          }
      },
      test("should produce the correct query when changing only the permissions") {
        ChangeResourceMetadataQuery
          .build(
            project = testProject,
            resourceIri = testResourceIri,
            resourceClassIri = testResourceClassIri,
            maybeLastModificationDate = Some(testLastModificationDate),
            maybeNewModificationDate = Some(testNewModificationDate),
            maybeLabel = None,
            maybePermissions = Some("CR knora-admin:Creator|V knora-admin:KnownUser"),
          )
          .map { case (lmd, update) =>
            assertTrue(
              lmd == testNewModificationDate,
              update.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                 |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |<http://rdfh.ch/0001/a-thing> knora-base:hasPermissions ?oldPermissions . } }
                                 |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-02T15:45:00Z"^^xsd:dateTime .
                                 |<http://rdfh.ch/0001/a-thing> knora-base:hasPermissions "CR knora-admin:Creator|V knora-admin:KnownUser" . } }
                                 |WHERE { <http://rdfh.ch/0001/a-thing> a <http://www.knora.org/ontology/0001/anything#Thing> .
                                 |<http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |OPTIONAL { <http://rdfh.ch/0001/a-thing> knora-base:hasPermissions ?oldPermissions . } }""".stripMargin,
            )
          }
      },
      test("should produce the correct query when changing both label and permissions") {
        ChangeResourceMetadataQuery
          .build(
            project = testProject,
            resourceIri = testResourceIri,
            resourceClassIri = testResourceClassIri,
            maybeLastModificationDate = Some(testLastModificationDate),
            maybeNewModificationDate = Some(testNewModificationDate),
            maybeLabel = Some("New Label"),
            maybePermissions = Some("CR knora-admin:Creator"),
          )
          .map { case (lmd, update) =>
            assertTrue(
              lmd == testNewModificationDate,
              update.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                 |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |<http://rdfh.ch/0001/a-thing> rdfs:label ?oldLabel .
                                 |<http://rdfh.ch/0001/a-thing> knora-base:hasPermissions ?oldPermissions . } }
                                 |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-02T15:45:00Z"^^xsd:dateTime .
                                 |<http://rdfh.ch/0001/a-thing> rdfs:label "New Label" .
                                 |<http://rdfh.ch/0001/a-thing> knora-base:hasPermissions "CR knora-admin:Creator" . } }
                                 |WHERE { <http://rdfh.ch/0001/a-thing> a <http://www.knora.org/ontology/0001/anything#Thing> .
                                 |<http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |OPTIONAL { <http://rdfh.ch/0001/a-thing> rdfs:label ?oldLabel . }
                                 |OPTIONAL { <http://rdfh.ch/0001/a-thing> knora-base:hasPermissions ?oldPermissions . } }""".stripMargin,
            )
          }
      },
      test("should produce the correct query when lastModificationDate is None (resource never modified before)") {
        ChangeResourceMetadataQuery
          .build(
            project = testProject,
            resourceIri = testResourceIri,
            resourceClassIri = testResourceClassIri,
            maybeLastModificationDate = None,
            maybeNewModificationDate = Some(testNewModificationDate),
            maybeLabel = Some("First Label"),
            maybePermissions = None,
          )
          .map { case (lmd, update) =>
            assertTrue(
              lmd == testNewModificationDate,
              update.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                 |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/a-thing> rdfs:label ?oldLabel . } }
                                 |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-02T15:45:00Z"^^xsd:dateTime .
                                 |<http://rdfh.ch/0001/a-thing> rdfs:label "First Label" . } }
                                 |WHERE { <http://rdfh.ch/0001/a-thing> a <http://www.knora.org/ontology/0001/anything#Thing> .
                                 |FILTER NOT EXISTS { <http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate ?anyLastModificationDate . }
                                 |OPTIONAL { <http://rdfh.ch/0001/a-thing> rdfs:label ?oldLabel . } }""".stripMargin,
            )
          }
      },
      test("should handle special characters in labels") {
        ChangeResourceMetadataQuery
          .build(
            project = testProject,
            resourceIri = testResourceIri,
            resourceClassIri = testResourceClassIri,
            maybeLastModificationDate = Some(testLastModificationDate),
            maybeNewModificationDate = Some(testNewModificationDate),
            maybeLabel = Some("Label with \"quotes\" and 'apostrophes'"),
            maybePermissions = None,
          )
          .map { case (lmd, update) =>
            assertTrue(
              lmd == testNewModificationDate,
              update.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                 |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |<http://rdfh.ch/0001/a-thing> rdfs:label ?oldLabel . } }
                                 |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-02T15:45:00Z"^^xsd:dateTime .
                                 |<http://rdfh.ch/0001/a-thing> rdfs:label "Label with \"quotes\" and \'apostrophes\'" . } }
                                 |WHERE { <http://rdfh.ch/0001/a-thing> a <http://www.knora.org/ontology/0001/anything#Thing> .
                                 |<http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |OPTIONAL { <http://rdfh.ch/0001/a-thing> rdfs:label ?oldLabel . } }""".stripMargin,
            )
          }
      },
      test("should handle special characters in permissions") {
        ChangeResourceMetadataQuery
          .build(
            project = testProject,
            resourceIri = testResourceIri,
            resourceClassIri = testResourceClassIri,
            maybeLastModificationDate = Some(testLastModificationDate),
            maybeNewModificationDate = Some(testNewModificationDate),
            maybeLabel = None,
            maybePermissions = Some("CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser"),
          )
          .map { case (lmd, update) =>
            assertTrue(
              lmd == testNewModificationDate,
              update.sparql == """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
                                 |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                                 |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
                                 |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                                 |DELETE { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |<http://rdfh.ch/0001/a-thing> knora-base:hasPermissions ?oldPermissions . } }
                                 |INSERT { GRAPH <http://www.knora.org/data/0001/anything> { <http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-02T15:45:00Z"^^xsd:dateTime .
                                 |<http://rdfh.ch/0001/a-thing> knora-base:hasPermissions "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser" . } }
                                 |WHERE { <http://rdfh.ch/0001/a-thing> a <http://www.knora.org/ontology/0001/anything#Thing> .
                                 |<http://rdfh.ch/0001/a-thing> knora-base:lastModificationDate "2023-08-01T10:30:00Z"^^xsd:dateTime .
                                 |OPTIONAL { <http://rdfh.ch/0001/a-thing> knora-base:hasPermissions ?oldPermissions . } }""".stripMargin,
            )
          }
      },
    ),
  )
}
