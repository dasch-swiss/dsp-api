/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.GroupBy
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ItemType

/**
 * Query-generator tests for [[ViewRestrictionsRepo]]. They assert on the rendered SPARQL string rather
 * than on triplestore results, pinning the query shape the service's correctness relies on: the
 * restriction pre-filter, deleted exclusion, the file-value marker, aggregation for the exact summary
 * counts, and deterministic ordering + windowing for the drill-down.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsQuerySpec extends ZIOSpecDefault with GoldenTest {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val thingClass = "http://www.knora.org/ontology/0001/anything#Thing"
  private val hasPicture = "http://www.knora.org/ontology/0001/anything#hasPicture"
  private val hidden     = Set("M knora-admin:ProjectMember", "V knora-admin:KnownUser")

  /** The common case: every resource carries exactly one class, so no most-specific-class filter is needed. */
  private val singleTyped = ViewRestrictionsRepo.ProjectClasses(Seq(thingClass), multiTyped = false)

  /** A project that asserts a class together with one of its ancestors — the filter must be kept. */
  private val multiTyped = ViewRestrictionsRepo.ProjectClasses(Seq(thingClass), multiTyped = true)

  private val resClassVar = SparqlBuilder.`var`("resClass")

  override def spec: Spec[TestEnvironment, Any] = suite("ViewRestrictionsRepo query generation")(
    suite("distinct permission literals")(
      test("resource variant selects only the distinct literal, restricted rows, excluding deleted") {
        val q = ViewRestrictionsRepo.distinctResourcePermissionsQuery(projectIri, singleTyped).getQueryString
        assertTrue(
          q.contains("SELECT DISTINCT ?permissions"),
          q.contains(s"knora-base:attachedToProject <${projectIri.value}>"),
          q.contains("knora-base:isDeleted false"),
          // the restriction pre-filter (drops rows that grant view to anonymous)
          q.contains("FILTER") && q.contains("knora-admin:UnknownUser"),
          // only the literal is projected — no per-object data crosses the wire…
          !q.contains("SELECT DISTINCT ?resource"),
          // …and nothing is capped
          !q.contains("LIMIT"),
        )
      },
      test("value variant excludes link values and is likewise uncapped") {
        val q = ViewRestrictionsRepo.distinctValuePermissionsQuery(projectIri, singleTyped).getQueryString
        assertTrue(
          q.contains("SELECT DISTINCT ?permissions"),
          q.contains("FILTER NOT EXISTS") && q.contains("knora-base:LinkValue"),
          !q.contains("LIMIT"),
        )
      },
      // Both variants omit the most-specific-class filter even for a multi-typed project: they project
      // only ?permissions under DISTINCT, so the filter can only remove rows, never change which literals
      // survive. Pinned as golden snapshots because dropping it is a large win on big projects
      // (measured on 46k resources / 265k values: 121s -> 16s, byte-identical results).
      test("resource variant omits the most-specific-class filter for a multi-typed project") {
        val q = ViewRestrictionsRepo.distinctResourcePermissionsQuery(projectIri, multiTyped).getQueryString
        assertGolden(q, "distinctResourcePermissions__multiTyped") &&
        assertTrue(!q.contains("rdfs:subClassOf+"))
      },
      test("value variant omits the most-specific-class filter for a multi-typed project") {
        val q = ViewRestrictionsRepo.distinctValuePermissionsQuery(projectIri, multiTyped).getQueryString
        assertGolden(q, "distinctValuePermissions__multiTyped") &&
        assertTrue(!q.contains("rdfs:subClassOf+"))
      },
    ),
    suite("aggregated counts")(
      test("resource counts group by class and COUNT DISTINCT, filtered to the hidden literals") {
        val q = ViewRestrictionsRepo.resourceCountQuery(projectIri, hidden, singleTyped).getQueryString
        assertTrue(
          q.contains("COUNT") && q.contains("DISTINCT"),
          q.contains("GROUP BY ?groupId"),
          // the classification is pushed into the query as a literal IN-list
          q.contains("M knora-admin:ProjectMember"),
          q.contains("V knora-admin:KnownUser"),
          // exact at any size: no row cap
          !q.contains("LIMIT"),
        )
      },
      test("value counts group by the carrying property in property mode") {
        val q = ViewRestrictionsRepo
          .valueCountQuery(projectIri, GroupBy.Property, ItemType.All, hidden, singleTyped)
          .getQueryString
        assertTrue(
          q.contains("COUNT") && q.contains("DISTINCT"),
          q.contains("GROUP BY ?groupId"),
          q.contains("?prop"),
          !q.contains("LIMIT"),
        )
      },
      test("itemType=Comment restricts the count to values carrying a comment") {
        val q = ViewRestrictionsRepo
          .valueCountQuery(projectIri, GroupBy.ResourceClass, ItemType.Comment, hidden, singleTyped)
          .getQueryString
        assertTrue(q.contains("knora-base:valueHasComment"))
      },
      test("itemType=Value excludes file values so File and Value cannot double-count") {
        val q = ViewRestrictionsRepo
          .valueCountQuery(projectIri, GroupBy.ResourceClass, ItemType.Value, hidden, singleTyped)
          .getQueryString
        assertTrue(q.contains("FILTER NOT EXISTS") && q.contains("knora-base:FileValue"))
      },
    ),
    suite("drill-down paging")(
      test("the page query orders deterministically and windows in SPARQL") {
        val q = ViewRestrictionsRepo
          .resourcePageQuery(
            projectIri,
            GroupBy.ResourceClass,
            ItemType.All,
            thingClass,
            offset = 50,
            limit = 25,
            singleTyped,
          )
          .getQueryString
        assertTrue(
          // ordering by the label-or-IRI key then the IRI makes paging reproducible
          q.contains("ORDER BY"),
          q.contains("?labelOrIri"),
          q.contains("LIMIT 25"),
          q.contains("OFFSET 50"),
          q.contains(thingClass),
        )
      },
      test("the page total is a COUNT DISTINCT over resources, unwindowed") {
        val q = ViewRestrictionsRepo
          .resourceCountForDrillDownQuery(projectIri, GroupBy.ResourceClass, ItemType.All, thingClass, singleTyped)
          .getQueryString
        assertTrue(
          q.contains("COUNT") && q.contains("DISTINCT"),
          q.contains(thingClass),
          // the count must span the whole result set, not one page
          !q.contains("LIMIT"),
          !q.contains("OFFSET"),
        )
      },
      test("row queries are scoped to the page's resource IRIs and carry no cap of their own") {
        val iris = Seq("http://rdfh.ch/0001/a", "http://rdfh.ch/0001/b")
        val rq   = ViewRestrictionsRepo.resourceQuery(projectIri, Some(thingClass), iris, singleTyped).getQueryString
        val vq   = ViewRestrictionsRepo
          .valueQuery(projectIri, Some(hasPicture), GroupBy.Property, iris, singleTyped)
          .getQueryString
        assertTrue(
          iris.forall(rq.contains),
          iris.forall(vq.contains),
          !rq.contains("LIMIT"),
          !vq.contains("LIMIT"),
          // the value rows still report file-ness and comments for the drill-down
          vq.contains("knora-base:FileValue"),
          vq.contains("knora-base:valueHasComment"),
          // …but never the still-image marker: visibility does not depend on a file being an image
          !vq.contains("knora-base:StillImageFileValue"),
          !vq.contains("imageClass"),
        )
      },
      test("in property mode the group filter applies to the carrying property") {
        val q = ViewRestrictionsRepo
          .resourcePageQuery(
            projectIri,
            GroupBy.Property,
            ItemType.All,
            hasPicture,
            offset = 0,
            limit = 25,
            singleTyped,
          )
          .getQueryString
        assertTrue(q.contains(hasPicture))
      },
    ),
    // The class hierarchy is resolved once per request (ProjectClasses) instead of by `rdfs:subClassOf`
    // traversals re-evaluated per result row. Rows scale as resources × properties × values, so these
    // paths dominated the endpoint's runtime — measured on `incunabula`, 3.60s → 0.72s for the value
    // count, with byte-identical results.
    //
    // Golden snapshots rather than substring assertions: what matters is *where* the VALUES clause and
    // the filters land, which `contains` cannot express, and an absence assertion like
    // `!q.contains("rdfs:subClassOf*")` would pass for the wrong reason if the path were ever rendered
    // unprefixed. See "Golden tests (preferred)" in docs/development/dsp-api-sparql-queries.md.
    suite("class-hierarchy resolution")(
      test("the single-typed project binds VALUES and emits no traversal") {
        val q = ViewRestrictionsRepo.withValues(
          ViewRestrictionsRepo.resourceCountQuery(projectIri, hidden, singleTyped),
          singleTyped.valuesClause(resClassVar),
        )
        assertGolden(q, "viewRestrictions__resourceCount__singleTyped")
      },
      test("the multi-typed project keeps the most-specific-class filter alongside VALUES") {
        val q = ViewRestrictionsRepo.withValues(
          ViewRestrictionsRepo.resourceCountQuery(projectIri, hidden, multiTyped),
          multiTyped.valuesClause(resClassVar),
        )
        assertGolden(q, "viewRestrictions__resourceCount__multiTyped")
      },
      test("the value count binds VALUES inside the WHERE block in property mode") {
        val q = ViewRestrictionsRepo.withValues(
          ViewRestrictionsRepo.valueCountQuery(projectIri, GroupBy.Property, ItemType.All, hidden, singleTyped),
          singleTyped.valuesClause(resClassVar),
        )
        assertGolden(q, "viewRestrictions__valueCount__property")
      },
      // Discovering no class must NOT leave ?resClass unconstrained: the original subClassOf* guard is
      // emitted instead, so the query still binds only resource classes.
      test("an empty class list falls back to the subClassOf* guard instead of dropping it") {
        val empty = ViewRestrictionsRepo.ProjectClasses(Seq.empty, multiTyped = false)
        val q     = ViewRestrictionsRepo.withValues(
          ViewRestrictionsRepo.resourceCountQuery(projectIri, hidden, empty),
          empty.valuesClause(resClassVar),
        )
        assertGolden(q, "viewRestrictions__resourceCount__noClassesDiscovered")
      },
      test("the class-discovery query is anchored on the project") {
        assertGolden(ViewRestrictionsRepo.projectClassesQuery(projectIri).getQueryString, "viewRestrictions__discovery")
      },
      // The probe must mirror the filter it gates exactly — `?subClass` unrestricted (it need not be a
      // resource class) and deleted resources excluded — or omitting the filter would change counts.
      test("the multi-typed probe mirrors the filter it gates") {
        assertGolden(ViewRestrictionsRepo.multiTypedQuery(projectIri).getQueryString, "viewRestrictions__multiTyped")
      },
      test("the drill-down page query binds VALUES once for the whole UNION") {
        val q = ViewRestrictionsRepo.withValues(
          ViewRestrictionsRepo
            .resourcePageQuery(projectIri, GroupBy.ResourceClass, ItemType.All, thingClass, 50, 25, singleTyped),
          singleTyped.valuesClause(resClassVar),
        )
        assertGolden(q, "viewRestrictions__drillDownPage")
      },
    ),
  )
}
