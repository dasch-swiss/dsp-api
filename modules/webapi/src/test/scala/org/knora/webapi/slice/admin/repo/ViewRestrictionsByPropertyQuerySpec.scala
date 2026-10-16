/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.apache.jena.query.QueryFactory
import org.apache.jena.sparql.algebra.Algebra
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ValueItemType

/**
 * Query-generator tests for [[ViewRestrictionsByPropertyRepo]].
 *
 * These pin the two shapes the report's performance and correctness rest on, both measured on a local LHTT
 * copy (105,983 resources, 859,723 values):
 *
 *   - the property IRI is **bound** in the triple pattern, not applied as a `FILTER` — 1,053ms against
 *     3,060ms;
 *   - no resource class is joined — 1,128ms against 2,380ms, both returning 66,484.
 *
 * Both are cases where copying the class report would have been slower, so they are asserted rather than
 * left to a reader's care.
 *
 * The last suite pins the whole shape rather than single features: every query is compared against the
 * RDF4J SparqlBuilder output it was ported from (see [[ViewRestrictionsByPropertyLegacyFixtures]]). The
 * port is a pure port, so the comparison is the strict one the rest of this migration uses — both sides
 * parsed by Jena with the prefix map cleared and re-serialised, which ignores whitespace and prefix
 * rendering but nothing else, group nesting included. The algebra of both is asserted alongside it, since
 * that is what join order and filter placement — the two things this report's performance rests on —
 * actually reduce to.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsByPropertyQuerySpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val hasText    = "http://www.knora.org/ontology/0001/anything#hasText"
  private val itemTypes  = List(ValueItemType.All, ValueItemType.File, ValueItemType.Value, ValueItemType.Comment)

  /** Parse and re-serialise, with prefixes expanded, so only the query itself is compared. */
  private def canonical(sparql: String): String = {
    val q = QueryFactory.create(sparql)
    q.getPrefixMapping.clearNsPrefixMap()
    q.toString
  }

  /** Unoptimised syntax-to-algebra translation: it keeps join order, group nesting and filter placement. */
  private def algebra(sparql: String): String = Algebra.compile(QueryFactory.create(sparql)).toString

  /** The ported queries, keyed exactly as [[ViewRestrictionsByPropertyLegacyFixtures]] keys them. */
  private val actualByKey: Map[String, String] = itemTypes.flatMap { it =>
    List(
      s"drillDownCountQuery-$it"              -> ViewRestrictionsByPropertyRepo.drillDownCountQuery(projectIri, hasText, it).sparql,
      s"drillDownResourcePageQuery-$it-50-25" -> ViewRestrictionsByPropertyRepo
        .drillDownResourcePageQuery(projectIri, hasText, it, offset = 50, limit = 25)
        .sparql,
      s"drillDownResourcePageQuery-$it-0-25" -> ViewRestrictionsByPropertyRepo
        .drillDownResourcePageQuery(projectIri, hasText, it, offset = 0, limit = 25)
        .sparql,
      s"drillDownRowsQuery-$it-two" -> ViewRestrictionsByPropertyRepo
        .drillDownRowsQuery(projectIri, hasText, it, Seq("http://rdfh.ch/0001/a", "http://rdfh.ch/0001/b"))
        .sparql,
      s"drillDownRowsQuery-$it-one" -> ViewRestrictionsByPropertyRepo
        .drillDownRowsQuery(projectIri, hasText, it, Seq("http://rdfh.ch/0001/a"))
        .sparql,
    )
  }.toMap

  override def spec: Spec[TestEnvironment, Any] = suite("ViewRestrictionsByPropertyRepo query generation")(
    suite("drill-down")(
      test("windows resources, not value rows, so the page unit matches the total") {
        // THE invariant. A property with maxCardinality > 1 (keywords, several titles) gives a resource
        // several restricted values. Windowing the value rows while the total counts distinct resources
        // pages two units against each other: 30 resources x 2 values = 60 rows, total 30, size 25 gives
        // totalPages 2, whose two pages consume rows 1-50 — resources 26-30 are then unreachable by any
        // page number the pagination block admits. So the LIMIT/OFFSET goes on a DISTINCT resource query.
        val q = ViewRestrictionsByPropertyRepo
          .drillDownResourcePageQuery(projectIri, hasText, ValueItemType.All, offset = 50, limit = 25)
          .sparql
        assertTrue(
          // The value is a join partner here, never a projected column: projecting it would reintroduce
          // one row per value and undo the whole point of this query.
          q.contains("SELECT DISTINCT ?resource ?labelOrIri"),
          q.contains("LIMIT 25"),
          q.contains("OFFSET 50"),
          q.contains("ORDER BY"),
        )
      },
      test("orders by label, falling back to the IRI so unlabelled resources still page deterministically") {
        val q = ViewRestrictionsByPropertyRepo
          .drillDownResourcePageQuery(projectIri, hasText, ValueItemType.All, offset = 0, limit = 25)
          .sparql
        assertTrue(q.contains("COALESCE"), q.contains("?labelOrIri"))
      },
      test("the row query is bounded by the page's IRIs and carries no window of its own") {
        // The IRI list IS the window, so every restricted value of a paged resource comes back together.
        // A resource can therefore never straddle a page boundary and be returned twice, partial each time.
        val q = ViewRestrictionsByPropertyRepo
          .drillDownRowsQuery(
            projectIri,
            hasText,
            ValueItemType.All,
            Seq("http://rdfh.ch/0001/a", "http://rdfh.ch/0001/b"),
          )
          .sparql
        assertTrue(
          q.contains("http://rdfh.ch/0001/a") && q.contains("http://rdfh.ch/0001/b"),
          !q.contains("LIMIT"),
          !q.contains("OFFSET"),
        )
      },
      test("projects the resource's own class, since a property spans classes") {
        // REQ-4.2. Without this the drill-down could not show that one property is restricted across
        // several classes, which is the finding the whole report exists to surface.
        val q = ViewRestrictionsByPropertyRepo
          .drillDownRowsQuery(projectIri, hasText, ValueItemType.All, Seq("http://rdfh.ch/0001/a"))
          .sparql
        assertTrue(q.contains("?resClass"))
      },
      test("lists only restricted values") {
        val rows = ViewRestrictionsByPropertyRepo
          .drillDownRowsQuery(projectIri, hasText, ValueItemType.All, Seq("http://rdfh.ch/0001/a"))
          .sparql
        assertTrue(rows.contains("knora-admin:UnknownUser"))
      },
      test("both drill-down queries restrict identically, so the total cannot describe a different row set") {
        val page = ViewRestrictionsByPropertyRepo
          .drillDownResourcePageQuery(projectIri, hasText, ValueItemType.All, offset = 0, limit = 25)
          .sparql
        val count =
          ViewRestrictionsByPropertyRepo.drillDownCountQuery(projectIri, hasText, ValueItemType.All).sparql
        assertTrue(
          page.contains("knora-admin:UnknownUser"),
          count.contains("knora-admin:UnknownUser"),
          page.contains(s"<$hasText>"),
          count.contains(s"<$hasText>"),
        )
      },
      test("the page total counts distinct resources — the same unit the page query windows") {
        val q =
          ViewRestrictionsByPropertyRepo.drillDownCountQuery(projectIri, hasText, ValueItemType.All).sparql
        assertTrue(
          q.contains("COUNT") && q.contains("DISTINCT"),
          q.contains("?resource"),
          !q.contains("LIMIT"),
          !q.contains("OFFSET"),
        )
      },
    ),
    suite("matches the RDF4J builder it was ported from")(
      ViewRestrictionsByPropertyLegacyFixtures.byKey.toList.sortBy(_._1).map { case (key, legacy) =>
        test(key) {
          val actual = actualByKey(key)
          assertTrue(
            canonical(actual) == canonical(legacy),
            algebra(actual) == algebra(legacy),
          )
        }
      }*,
    ),
  )
}
