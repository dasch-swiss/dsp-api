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
import org.knora.webapi.slice.admin.domain.model.Authorship
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri

object ChangeResourceAuthorshipQuerySpec extends ZIOSpecDefault {

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
  private val ada       = Authorship.unsafeFrom("Ada Lovelace")
  private val alan      = Authorship.unsafeFrom("Alan Turing")

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ChangeResourceAuthorshipQuerySpec")(
    suite("build")(
      // Regression guard for the silent no-op bug: the update MUST use `WITH <graph>` so the named
      // project graph applies to the WHERE clause too. With GRAPH-wrapped DELETE/INSERT and an
      // ungraphed WHERE, the WHERE matches the (empty) default graph on production Fuseki and the
      // whole update no-ops. So: `WITH <graph>` present, and no `GRAPH` wrapper anywhere.
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
            val q = update.sparql
            assertTrue(
              lmd == testNewModificationDate,
              q.contains(s"WITH <$dataGraph>"),
              !q.contains("GRAPH"),
              // the new authorship values are inserted as string literals
              q.contains("knora-base:hasResourceAuthorship \"Ada Lovelace\""),
              q.contains("knora-base:hasResourceAuthorship \"Alan Turing\""),
              // existing authorship is deleted via the bound variable, matched optionally in WHERE
              q.contains("knora-base:hasResourceAuthorship ?oldAuthorship"),
              q.contains("OPTIONAL { <http://rdfh.ch/0001/a-thing> knora-base:hasResourceAuthorship ?oldAuthorship"),
              // the existing lastModificationDate is matched in WHERE and bumped to the new one
              q.contains("knora-base:lastModificationDate \"2023-08-01T10:30:00Z\"^^xsd:dateTime"),
              q.contains("knora-base:lastModificationDate \"2023-08-02T15:45:00Z\"^^xsd:dateTime"),
            )
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
            val q = update.sparql
            assertTrue(
              lmd == testNewModificationDate,
              q.contains(s"WITH <$dataGraph>"),
              !q.contains("GRAPH"),
              // no authorship literal is inserted (clearing), but the existing one is still deleted
              !q.contains("knora-base:hasResourceAuthorship \""),
              q.contains("knora-base:hasResourceAuthorship ?oldAuthorship"),
              q.contains("knora-base:lastModificationDate \"2023-08-02T15:45:00Z\"^^xsd:dateTime"),
            )
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
            val q = update.sparql
            assertTrue(
              lmd == testNewModificationDate,
              q.contains(s"WITH <$dataGraph>"),
              !q.contains("GRAPH"),
              q.contains("FILTER NOT EXISTS"),
              q.contains("knora-base:hasResourceAuthorship \"Ada Lovelace\""),
              q.contains("knora-base:lastModificationDate \"2023-08-02T15:45:00Z\"^^xsd:dateTime"),
            )
          }
      },
    ),
  )
}
