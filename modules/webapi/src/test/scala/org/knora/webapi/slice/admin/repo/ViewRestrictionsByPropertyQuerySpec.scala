/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

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
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsByPropertyQuerySpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val hasText    = "http://www.knora.org/ontology/0001/anything#hasText"

  override def spec: Spec[TestEnvironment, Any] = suite("ViewRestrictionsByPropertyRepo query generation")(
    suite("value counts")(
      test("groups by permission literal alone and applies no permission filter") {
        val q = ViewRestrictionsByPropertyRepo.valueCountsQuery(projectIri, hasText, ValueItemType.All).getQueryString
        assertTrue(
          q.contains("COUNT") && q.contains("DISTINCT"),
          q.contains("GROUP BY ?permissions"),
          // The route has already narrowed to one property, so there is no second grouping axis.
          !q.contains("GROUP BY ?prop"),
          // No literal IN-list: the absence of a permission filter is what makes totalValues derivable
          // by summing the rows.
          !q.contains("knora-admin:ProjectMember"),
          // exact at any size
          !q.contains("LIMIT"),
        )
      },
      test("binds the property IRI in the pattern rather than filtering on it") {
        // Measured on LHTT for lhtt:hasTitle: bound 1,053ms, filtered 3,060ms. The class report filters
        // because its grouping key is the class and the property varies; here the property is the one
        // fixed thing, so it belongs in the pattern.
        val q = ViewRestrictionsByPropertyRepo.valueCountsQuery(projectIri, hasText, ValueItemType.All).getQueryString
        assertTrue(
          q.contains(s"<$hasText>"),
          !q.contains("FILTER") || !q.contains("?prop ="),
          !q.contains("?prop"),
        )
      },
      test("joins no resource class, so none of the class machinery is involved") {
        // Measured on LHTT: with the class join 2,380ms, without 1,128ms, both 66,484. The class report
        // needs ProjectClasses, its VALUES clause and the most-specific-class filter because it groups by
        // class; counting DISTINCT values under a bound property cannot double-count.
        val q = ViewRestrictionsByPropertyRepo.valueCountsQuery(projectIri, hasText, ValueItemType.All).getQueryString
        assertTrue(
          !q.contains("?resClass"),
          !q.contains("VALUES"),
          !q.contains("rdfs:subClassOf+"),
        )
      },
      test("scopes by attachedToProject and never by GRAPH") {
        // Graph scoping undercounts: a project's resources span one data graph per ontology while
        // projectDataNamedGraphV2 derives exactly one. Measured on the local anything project as 65
        // resources in one graph and 6 in another.
        val q = ViewRestrictionsByPropertyRepo.valueCountsQuery(projectIri, hasText, ValueItemType.All).getQueryString
        assertTrue(
          q.contains(s"knora-base:attachedToProject <${projectIri.value}>"),
          !q.contains("GRAPH"),
        )
      },
      test("itemType narrows the counted values") {
        val comment =
          ViewRestrictionsByPropertyRepo.valueCountsQuery(projectIri, hasText, ValueItemType.Comment).getQueryString
        val value =
          ViewRestrictionsByPropertyRepo.valueCountsQuery(projectIri, hasText, ValueItemType.Value).getQueryString
        val file =
          ViewRestrictionsByPropertyRepo.valueCountsQuery(projectIri, hasText, ValueItemType.File).getQueryString
        assertTrue(
          comment.contains("knora-base:valueHasComment"),
          value.contains("FILTER NOT EXISTS") && value.contains("knora-base:FileValue"),
          file.contains("knora-base:FileValue"),
        )
      },
      test("link values are always excluded") {
        val q = ViewRestrictionsByPropertyRepo.valueCountsQuery(projectIri, hasText, ValueItemType.All).getQueryString
        assertTrue(q.contains("FILTER NOT EXISTS") && q.contains("knora-base:LinkValue"))
      },
    ),
    suite("drill-down")(
      test("windows resources, not value rows, so the page unit matches the total") {
        // THE invariant. A property with maxCardinality > 1 (keywords, several titles) gives a resource
        // several restricted values. Windowing the value rows while the total counts distinct resources
        // pages two units against each other: 30 resources x 2 values = 60 rows, total 30, size 25 gives
        // totalPages 2, whose two pages consume rows 1-50 — resources 26-30 are then unreachable by any
        // page number the pagination block admits. So the LIMIT/OFFSET goes on a DISTINCT resource query.
        val q = ViewRestrictionsByPropertyRepo
          .drillDownResourcePageQuery(projectIri, hasText, ValueItemType.All, offset = 50, limit = 25)
          .getQueryString
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
          .getQueryString
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
          .getQueryString
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
          .getQueryString
        assertTrue(q.contains("?resClass"))
      },
      test("lists only restricted values, unlike the counts") {
        // The drill-down shows restrictions; the counts deliberately keep every literal so the population
        // stays derivable from the same rows.
        val rows = ViewRestrictionsByPropertyRepo
          .drillDownRowsQuery(projectIri, hasText, ValueItemType.All, Seq("http://rdfh.ch/0001/a"))
          .getQueryString
        val counts =
          ViewRestrictionsByPropertyRepo.valueCountsQuery(projectIri, hasText, ValueItemType.All).getQueryString
        assertTrue(
          rows.contains("knora-admin:UnknownUser"),
          !counts.contains("knora-admin:UnknownUser"),
        )
      },
      test("both drill-down queries restrict identically, so the total cannot describe a different row set") {
        val page = ViewRestrictionsByPropertyRepo
          .drillDownResourcePageQuery(projectIri, hasText, ValueItemType.All, offset = 0, limit = 25)
          .getQueryString
        val count =
          ViewRestrictionsByPropertyRepo.drillDownCountQuery(projectIri, hasText, ValueItemType.All).getQueryString
        assertTrue(
          page.contains("knora-admin:UnknownUser"),
          count.contains("knora-admin:UnknownUser"),
          page.contains(s"<$hasText>"),
          count.contains(s"<$hasText>"),
        )
      },
      test("the page total counts distinct resources — the same unit the page query windows") {
        val q =
          ViewRestrictionsByPropertyRepo.drillDownCountQuery(projectIri, hasText, ValueItemType.All).getQueryString
        assertTrue(
          q.contains("COUNT") && q.contains("DISTINCT"),
          q.contains("?resource"),
          !q.contains("LIMIT"),
          !q.contains("OFFSET"),
        )
      },
    ),
  )
}
