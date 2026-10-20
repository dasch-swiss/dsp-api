/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.apache.jena.query.QueryFactory
import org.apache.jena.sparql.algebra.Algebra
import org.apache.jena.sparql.algebra.OpVisitorBase
import org.apache.jena.sparql.algebra.OpWalker
import org.apache.jena.sparql.algebra.op.OpJoin
import org.junit.runner.RunWith
import zio.test.*

import org.knora.sparqlbuilder.Iri
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest
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
 * The last suites pin the permission-literal selection the drill-down shares with the counts, and the graph
 * scoping of the page and count queries.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsByPropertyQuerySpec extends ZIOSpecDefault with GoldenTest {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val hasText    = "http://www.knora.org/ontology/0001/anything#hasText"
  private val graph      = Iri.unsafeFrom("http://www.knora.org/data/0001/anything")
  private val itemTypes  = List(ValueItemType.All, ValueItemType.File, ValueItemType.Value, ValueItemType.Comment)

  private val restricted = Set("M knora-admin:ProjectMember", "V knora-admin:UnknownUser|RV knora-admin:KnownUser")

  /** Every drill-down query, for each item type. */
  private val allQueries: Map[String, String] = itemTypes.flatMap { it =>
    List(
      s"drillDownCountQuery-$it" -> ViewRestrictionsByPropertyRepo.drillDownCountQuery(
        graph,
        hasText,
        it,
        restricted,
      ),
      s"drillDownResourcePageQuery-$it" -> ViewRestrictionsByPropertyRepo
        .drillDownResourcePageQuery(graph, hasText, it, restricted, offset = 0, limit = 25),
      s"drillDownRowsQuery-$it" -> ViewRestrictionsByPropertyRepo
        .drillDownRowsQuery(projectIri, hasText, it, restricted, Seq("http://rdfh.ch/0001/a")),
    ).map { case (k, q) => k -> q.sparql }
  }.toMap

  private val goldenLiterals =
    Set("M knora-admin:ProjectMember", "V knora-admin:KnownUser|RV \"q\" knora-admin:UnknownUser")

  private val goldenQueries: Map[String, String] = Map(
    "drillDownResourcePage-All" -> ViewRestrictionsByPropertyRepo
      .drillDownResourcePageQuery(graph, hasText, ValueItemType.All, goldenLiterals, offset = 50, limit = 25),
    "drillDownResourcePage-File" -> ViewRestrictionsByPropertyRepo
      .drillDownResourcePageQuery(graph, hasText, ValueItemType.File, goldenLiterals, offset = 50, limit = 25),
    "drillDownRows-All" -> ViewRestrictionsByPropertyRepo
      .drillDownRowsQuery(
        projectIri,
        hasText,
        ValueItemType.All,
        goldenLiterals,
        Seq("http://rdfh.ch/0001/a", "http://rdfh.ch/0001/b"),
      ),
    "drillDownRows-File" -> ViewRestrictionsByPropertyRepo
      .drillDownRowsQuery(
        projectIri,
        hasText,
        ValueItemType.File,
        goldenLiterals,
        Seq("http://rdfh.ch/0001/a", "http://rdfh.ch/0001/b"),
      ),
    "drillDownCount-All" -> ViewRestrictionsByPropertyRepo.drillDownCountQuery(
      graph,
      hasText,
      ValueItemType.All,
      goldenLiterals,
    ),
    "drillDownCount-File" -> ViewRestrictionsByPropertyRepo.drillDownCountQuery(
      graph,
      hasText,
      ValueItemType.File,
      goldenLiterals,
    ),
  ).view.mapValues(_.sparql).toMap

  private val pageAndCountQueries = allQueries.filterNot(_._1.startsWith("drillDownRowsQuery"))

  /** Whether the optimised algebra joins two independently evaluated parts. */
  private def hasIndependentJoin(sparql: String): Boolean = {
    var found = false
    OpWalker.walk(
      Algebra.optimize(Algebra.compile(QueryFactory.create(sparql))),
      new OpVisitorBase {
        override def visit(op: OpJoin): Unit = found = true
      },
    )
    found
  }

  override def spec: Spec[TestEnvironment, Any] = suite("ViewRestrictionsByPropertyRepo query generation")(
    suite("drill-down")(
      test("windows resources, not value rows, so the page unit matches the total") {
        // THE invariant. A property with maxCardinality > 1 (keywords, several titles) gives a resource
        // several restricted values. Windowing the value rows while the total counts distinct resources
        // pages two units against each other: 30 resources x 2 values = 60 rows, total 30, size 25 gives
        // totalPages 2, whose two pages consume rows 1-50 — resources 26-30 are then unreachable by any
        // page number the pagination block admits. So the LIMIT/OFFSET goes on a DISTINCT resource query.
        val q = ViewRestrictionsByPropertyRepo
          .drillDownResourcePageQuery(graph, hasText, ValueItemType.All, restricted, offset = 50, limit = 25)
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
          .drillDownResourcePageQuery(graph, hasText, ValueItemType.All, restricted, offset = 0, limit = 25)
          .sparql
        assertTrue(q.contains("COALESCE"), q.contains("?labelOrIri"))
      },
      test("the row query is anchored by VALUES on the page's IRIs and carries no window of its own") {
        // The IRI list IS the window, so every restricted value of a paged resource comes back together.
        // A resource can therefore never straddle a page boundary and be returned twice, partial each time.
        val q = ViewRestrictionsByPropertyRepo
          .drillDownRowsQuery(
            projectIri,
            hasText,
            ValueItemType.All,
            restricted,
            Seq("http://rdfh.ch/0001/a", "http://rdfh.ch/0001/b"),
          )
          .sparql
        assertTrue(
          q.contains("http://rdfh.ch/0001/a") && q.contains("http://rdfh.ch/0001/b"),
          q.contains("WHERE {\n  VALUES ?resource {"),
          !q.contains("?resource IN"),
          !q.contains("LIMIT"),
          !q.contains("OFFSET"),
        )
      },
      test("projects the resource's own class, since a property spans classes") {
        // REQ-4.2. Without this the drill-down could not show that one property is restricted across
        // several classes, which is the finding the whole report exists to surface.
        val q = ViewRestrictionsByPropertyRepo
          .drillDownRowsQuery(projectIri, hasText, ValueItemType.All, restricted, Seq("http://rdfh.ch/0001/a"))
          .sparql
        assertTrue(q.contains("?resClass"))
      },
      test("lists only restricted values") {
        val rows = ViewRestrictionsByPropertyRepo
          .drillDownRowsQuery(projectIri, hasText, ValueItemType.All, restricted, Seq("http://rdfh.ch/0001/a"))
          .sparql
        assertTrue(rows.contains("STR(?permissions) IN"))
      },
      test("both drill-down queries restrict identically, so the total cannot describe a different row set") {
        val page = ViewRestrictionsByPropertyRepo
          .drillDownResourcePageQuery(graph, hasText, ValueItemType.All, restricted, offset = 0, limit = 25)
          .sparql
        val count =
          ViewRestrictionsByPropertyRepo.drillDownCountQuery(graph, hasText, ValueItemType.All, restricted).sparql
        // the count's whole WHERE body is the page's selection
        val countBody = count.substring(count.indexOf("WHERE {\n") + "WHERE {\n".length, count.lastIndexOf("\n}"))
        assertTrue(
          countBody.contains("VALUES ?permissions {"),
          countBody.contains(s"<$hasText>"),
          page.contains(countBody),
        )
      },
      test("the page total counts distinct resources — the same unit the page query windows") {
        val q =
          ViewRestrictionsByPropertyRepo.drillDownCountQuery(graph, hasText, ValueItemType.All, restricted).sparql
        assertTrue(
          q.contains("COUNT(*)") && q.contains("SELECT DISTINCT ?resource\n"),
          q.contains("?resource"),
          !q.contains("LIMIT"),
          !q.contains("OFFSET"),
        )
      },
    ),
    suite("drill-down golden queries")(
      test("TEMP capture") {
        val dir = java.lang.System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")
        goldenQueries.foreach { case (n, q) =>
          java.nio.file.Files
            .writeString(java.nio.file.Paths.get(dir, s"ViewRestrictionsByPropertyQuerySpec__$n.txt"), q)
        }
        assertTrue(true)
      },
      test("drillDownResourcePage-All")(
        assertGolden(goldenQueries("drillDownResourcePage-All"), "drillDownResourcePage-All"),
      ),
      test("drillDownResourcePage-File")(
        assertGolden(goldenQueries("drillDownResourcePage-File"), "drillDownResourcePage-File"),
      ),
      test("drillDownRows-All")(assertGolden(goldenQueries("drillDownRows-All"), "drillDownRows-All")),
      test("drillDownRows-File")(assertGolden(goldenQueries("drillDownRows-File"), "drillDownRows-File")),
      test("drillDownCount-All")(assertGolden(goldenQueries("drillDownCount-All"), "drillDownCount-All")),
      test("drillDownCount-File")(assertGolden(goldenQueries("drillDownCount-File"), "drillDownCount-File")),
    ),
    suite("selects restricted values by permission literal")(
      test("the row queries filter on STR(?permissions) IN the passed literals") {
        assertTrue(
          allQueries
            .filter(_._1.startsWith("drillDownRowsQuery"))
            .values
            .forall(q =>
              q.contains(
                """FILTER (STR(?permissions) IN ("M knora-admin:ProjectMember", "V knora-admin:UnknownUser|RV knora-admin:KnownUser"))""",
              ),
            ),
        )
      },
      test("the page and count queries are driven by a VALUES list of the passed literals") {
        assertTrue(
          pageAndCountQueries.values.forall(q =>
            q.contains(
              """VALUES ?permissions { "M knora-admin:ProjectMember" "V knora-admin:UnknownUser|RV knora-admin:KnownUser" }""",
            ) && !q.contains("STR(?permissions)"),
          ),
        )
      },
      test("no query uses REGEX or mentions LinkValue") {
        assertTrue(allQueries.values.forall(q => !q.contains("REGEX") && !q.contains("LinkValue")))
      },
      test("every query binds the property in the triple pattern") {
        assertTrue(allQueries.values.forall(_.contains(s"<$hasText> ?value")))
      },
    ),
    suite("graph scoping")(
      test("the page and count queries read the given graph and do not join attachedToProject") {
        assertTrue(
          pageAndCountQueries.values.forall(_.contains("GRAPH <http://www.knora.org/data/0001/anything>")),
          pageAndCountQueries.values.forall(!_.contains("attachedToProject")),
        )
      },
      suite("the page and count queries join the sub-select barrier as a sequence")(
        pageAndCountQueries.toList.sortBy(_._1).map { case (name, q) =>
          test(name)(assertTrue(!hasIndependentJoin(q)))
        }*,
      ),
    ),
  )
}
