/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.*
import zio.NonEmptyChunk
import zio.test.*

import java.time.Instant

import org.knora.webapi.GoldenTest
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri

object ChangeResourceMetadataQuerySpec extends ZIOSpecDefault with GoldenTest {

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

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ChangeResourceMetadataQuerySpec")(
    suite("build")(
      // The generated query is compared against a golden file (src/test/resources/.../<suffix>.txt).
      // Regression guard for the silent no-op bug (DEV-6669): the golden output MUST scope the named
      // project graph with `WITH <graph>` (and no `GRAPH` wrapper), so the WHERE clause matches the
      // resource in its graph rather than the dataset default graph -- which is empty on a store without
      // a union default graph (e.g. the in-memory triplestore used in tests) and would make the update
      // no-op.
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
            assertTrue(lmd == testNewModificationDate) && assertGolden(update.sparql, "label")
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
            assertTrue(lmd == testNewModificationDate) && assertGolden(update.sparql, "permissions")
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
            assertTrue(lmd == testNewModificationDate) && assertGolden(update.sparql, "labelAndPermissions")
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
            assertTrue(lmd == testNewModificationDate) && assertGolden(update.sparql, "neverModified")
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
            assertTrue(lmd == testNewModificationDate) && assertGolden(update.sparql, "specialCharsLabel")
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
            assertTrue(lmd == testNewModificationDate) && assertGolden(update.sparql, "specialCharsPermissions")
          }
      },
    ),
  )
}
