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
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri

object ChangeResourceAuthorshipQuerySpec extends ZIOSpecDefault with GoldenTest {

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

  private val ada  = Authorship.unsafeFrom("Ada Lovelace")
  private val alan = Authorship.unsafeFrom("Alan Turing")

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ChangeResourceAuthorshipQuerySpec")(
    suite("build")(
      // The generated query is compared against a golden file (src/test/resources/.../<suffix>.txt).
      // Regression guard for the silent no-op bug: the golden output MUST scope the named project graph
      // with `WITH <graph>` (and no `GRAPH` wrapper), so the WHERE clause matches the resource in its
      // graph rather than the dataset default graph -- which is empty on a store without a union default
      // graph (e.g. the in-memory triplestore used in tests) and would make the whole update no-op.
      test("scopes the named graph with WITH (not GRAPH-wrapped clauses) when setting authorship") {
        ChangeResourceAuthorshipQuery
          .build(
            project = testProject,
            resourceIri = testResourceIri,
            resourceClassIri = testResourceClassIri,
            maybeLastModificationDate = Some(testLastModificationDate),
            maybeNewModificationDate = Some(testNewModificationDate),
            authorship = Seq(ada, alan),
          )
          .map { case (lmd, update) =>
            assertTrue(lmd == testNewModificationDate) && assertGolden(update.sparql, "setAuthorship")
          }
      },
      test("inserts no authorship triples when clearing authorship (empty sequence)") {
        ChangeResourceAuthorshipQuery
          .build(
            project = testProject,
            resourceIri = testResourceIri,
            resourceClassIri = testResourceClassIri,
            maybeLastModificationDate = Some(testLastModificationDate),
            maybeNewModificationDate = Some(testNewModificationDate),
            authorship = Seq.empty,
          )
          .map { case (lmd, update) =>
            assertTrue(lmd == testNewModificationDate) && assertGolden(update.sparql, "clearAuthorship")
          }
      },
      test("uses FILTER NOT EXISTS for lastModificationDate when the resource was never modified") {
        ChangeResourceAuthorshipQuery
          .build(
            project = testProject,
            resourceIri = testResourceIri,
            resourceClassIri = testResourceClassIri,
            maybeLastModificationDate = None,
            maybeNewModificationDate = Some(testNewModificationDate),
            authorship = Seq(ada),
          )
          .map { case (lmd, update) =>
            assertTrue(lmd == testNewModificationDate) && assertGolden(update.sparql, "neverModified")
          }
      },
    ),
  )
}
