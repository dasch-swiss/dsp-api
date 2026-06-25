/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri

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

  private val testResourceIri      = ResourceIri.unsafeFrom("http://rdfh.ch/0001/a-thing")
  private val testResourceClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0001/anything#Thing".toSmartIri)
  private val testLastModificationDate = LastModificationDate.from(Instant.parse("2023-08-01T10:30:00Z"))
  private val testNewModificationDate  = LastModificationDate.from(Instant.parse("2023-08-02T15:45:00Z"))

  private val dataGraph = "http://www.knora.org/data/0001/anything"

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ChangeResourceMetadataQuerySpec")(
    suite("build")(
      // Regression guard for the silent no-op bug (DEV-6669): the update MUST scope the named project
      // graph with `WITH <graph>` so the WHERE clause matches the resource in its graph. With
      // GRAPH-wrapped DELETE/INSERT and an ungraphed WHERE, the WHERE matches the (empty) default graph
      // on production Fuseki and the whole update no-ops. So: `WITH <graph>` present, no `GRAPH` wrapper.
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
            val q = update.sparql
            assertTrue(
              lmd == testNewModificationDate,
              q.contains(s"WITH <$dataGraph>"),
              !q.contains("GRAPH"),
              q.contains("rdfs:label \"Updated Resource Label\""),
              q.contains("rdfs:label ?oldLabel"),
              q.contains("OPTIONAL { <http://rdfh.ch/0001/a-thing> rdfs:label ?oldLabel"),
              q.contains("knora-base:lastModificationDate \"2023-08-01T10:30:00Z\"^^xsd:dateTime"),
              q.contains("knora-base:lastModificationDate \"2023-08-02T15:45:00Z\"^^xsd:dateTime"),
              !q.contains("knora-base:hasPermissions"),
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
            val q = update.sparql
            assertTrue(
              lmd == testNewModificationDate,
              q.contains(s"WITH <$dataGraph>"),
              !q.contains("GRAPH"),
              q.contains("knora-base:hasPermissions \"CR knora-admin:Creator|V knora-admin:KnownUser\""),
              q.contains("knora-base:hasPermissions ?oldPermissions"),
              !q.contains("rdfs:label"),
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
            val q = update.sparql
            assertTrue(
              lmd == testNewModificationDate,
              q.contains(s"WITH <$dataGraph>"),
              !q.contains("GRAPH"),
              q.contains("rdfs:label \"New Label\""),
              q.contains("rdfs:label ?oldLabel"),
              q.contains("knora-base:hasPermissions \"CR knora-admin:Creator\""),
              q.contains("knora-base:hasPermissions ?oldPermissions"),
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
            val q = update.sparql
            assertTrue(
              lmd == testNewModificationDate,
              q.contains(s"WITH <$dataGraph>"),
              !q.contains("GRAPH"),
              q.contains("FILTER NOT EXISTS"),
              q.contains("rdfs:label \"First Label\""),
              q.contains("knora-base:lastModificationDate \"2023-08-02T15:45:00Z\"^^xsd:dateTime"),
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
            val q = update.sparql
            assertTrue(
              lmd == testNewModificationDate,
              q.contains(s"WITH <$dataGraph>"),
              !q.contains("GRAPH"),
              // quotes and apostrophes are escaped in the emitted SPARQL string literal
              q.contains("\\\"quotes\\\""),
              q.contains("\\'apostrophes\\'"),
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
            val q = update.sparql
            assertTrue(
              lmd == testNewModificationDate,
              q.contains(s"WITH <$dataGraph>"),
              !q.contains("GRAPH"),
              q.contains(
                "knora-base:hasPermissions \"CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser\"",
              ),
            )
          }
      },
    ),
  )
}
