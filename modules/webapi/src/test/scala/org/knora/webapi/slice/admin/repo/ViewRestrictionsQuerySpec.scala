/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ItemType

/**
 * Query-generator tests for [[ViewRestrictionsRepo]]. They assert on the rendered SPARQL string rather
 * than on triplestore results, pinning the query shape the service's correctness relies on: the
 * permission grouping the counts rely on, deleted exclusion, the file-value marker, and deterministic
 * ordering + windowing for the drill-down.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsQuerySpec extends ZIOSpecDefault with GoldenTest {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val thingClass = "http://www.knora.org/ontology/0001/anything#Thing"
  private val hasPicture = "http://www.knora.org/ontology/0001/anything#hasPicture"

  /** The common case: every resource carries exactly one class, so no most-specific-class filter is needed. */
  private val singleTyped = ViewRestrictionsRepo.ProjectClasses(Seq(thingClass), multiTyped = false)

  /** A project that asserts a class together with one of its ancestors — the filter must be kept. */
  private val multiTyped = ViewRestrictionsRepo.ProjectClasses(Seq(thingClass), multiTyped = true)

  override def spec: Spec[TestEnvironment, Any] = suite("ViewRestrictionsRepo query generation")(
    suite("permission-grouped counts (stepped report)")(
      test("resource counts group by class AND literal, with no permission filter at all") {
        val q = ViewRestrictionsRepo
          .resourceCountsByClassAndPermissionQuery(projectIri, singleTyped)
          .getQueryString
        assertTrue(
          q.contains("COUNT") && q.contains("DISTINCT"),
          // Both axes in the grouping key: this is what lets one query answer every audience and both
          // restriction states at once.
          q.contains("GROUP BY ?resClass ?permissions"),
          // No literal IN-list — the absence of a permission filter is exactly what makes the class's
          // whole population derivable by summing its rows.
          !q.contains("M knora-admin:ProjectMember"),
          !q.contains("V knora-admin:KnownUser"),
          // The creator is neither projected nor constrained, so the join is dropped (see resourceCore).
          !q.contains("?creator"),
          // exact at any size: no row cap
          !q.contains("LIMIT"),
        )
      },
      test("value counts group by literal alone and are narrowed to one class by FILTER, not by chunking") {
        val q = ViewRestrictionsRepo
          .valueCountsByPermissionQuery(projectIri, thingClass, ItemType.All, singleTyped)
          .getQueryString
        assertTrue(
          q.contains("GROUP BY ?permissions"),
          // one class per request — the route is the unit of work, so there is no second grouping axis
          !q.contains("GROUP BY ?resClass"),
          q.contains("FILTER") && q.contains(thingClass),
          // link values stay excluded, as in every other value query
          q.contains("FILTER NOT EXISTS") && q.contains("knora-base:LinkValue"),
          !q.contains("?creator"),
          !q.contains("LIMIT"),
        )
      },
      test("itemType still narrows the value counts") {
        val comment = ViewRestrictionsRepo
          .valueCountsByPermissionQuery(projectIri, thingClass, ItemType.Comment, singleTyped)
          .getQueryString
        val value = ViewRestrictionsRepo
          .valueCountsByPermissionQuery(projectIri, thingClass, ItemType.Value, singleTyped)
          .getQueryString
        assertTrue(
          comment.contains("knora-base:valueHasComment"),
          value.contains("FILTER NOT EXISTS") && value.contains("knora-base:FileValue"),
        )
      },
      test("the most-specific-class filter is kept, so a multi-typed resource is counted once") {
        // Load-bearing for the grouping: without it a resource asserted as several classes in one
        // hierarchy binds ?resClass once per class, double-counting it and overstating totalResources.
        val q = ViewRestrictionsRepo
          .resourceCountsByClassAndPermissionQuery(projectIri, multiTyped)
          .getQueryString
        assertTrue(q.contains("rdfs:subClassOf+"))
      },
    ),
    suite("project class discovery")(
      test("the closure walk sits outside a sub-select, so it runs per class and not per resource row") {
        val q = ViewRestrictionsRepo.projectClassesQuery(projectIri).getQueryString
        assertTrue(
          // The project scan is deduplicated to the classes in use *before* the path sees them. Without the
          // sub-select the store materializes one row per (resource, asserted type) for the whole project
          // and probes the closure once per row -- Fuseki-cancelled at the 20s tier on a large project.
          q.contains("SELECT DISTINCT ?resClass") && q.indexOf("SELECT DISTINCT ?resClass") != q.lastIndexOf(
            "SELECT DISTINCT ?resClass",
          ),
          // The guard itself stays: attachedToProject is also carried by list nodes and by the project's
          // own ontologies, so dropping it would let ListNode and owl:Ontology into the class list.
          q.contains("rdfs:subClassOf*") && q.contains("knora-base:Resource"),
          // and the closure walk is outside the sub-select, i.e. after its closing brace
          q.indexOf("rdfs:subClassOf*") > q.lastIndexOf("a ?resClass"),
          q.contains(s"knora-base:attachedToProject <${projectIri.value}>"),
          !q.contains("GRAPH"),
        )
      },
    ),
    suite("every query is scoped by attachedToProject")(
      test("no view-restrictions query uses GRAPH scoping") {
        // Deliberate, and measured: a project's resources span one data graph per ontology, while
        // ProjectService.projectDataNamedGraphV2 derives exactly one from shortcode + shortname. On the
        // local `anything` project that is 65 resources in …/data/0001/anything and 6 more in
        // …/data/0001/freetest, so scoping to the derived graph undercounts by those 6 — and silently,
        // because a graph with no matches yields no rows rather than an error. The join stays.
        val all = Seq(
          ViewRestrictionsRepo.resourceCountsByClassAndPermissionQuery(projectIri, singleTyped).getQueryString,
          ViewRestrictionsRepo
            .valueCountsByPermissionQuery(projectIri, thingClass, ItemType.All, singleTyped)
            .getQueryString,
          ViewRestrictionsRepo
            .resourcePageQuery(projectIri, ItemType.All, thingClass, offset = 0, limit = 25, singleTyped)
            .getQueryString,
        )
        assertTrue(
          all.forall(_.contains(s"knora-base:attachedToProject <${projectIri.value}>")),
          all.forall(!_.contains("GRAPH")),
        )
      },
    ),
    suite("drill-down paging")(
      test("the page query orders deterministically and windows in SPARQL") {
        val q = ViewRestrictionsRepo
          .resourcePageQuery(projectIri, ItemType.All, thingClass, offset = 50, limit = 25, singleTyped)
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
          .resourceCountForDrillDownQuery(projectIri, ItemType.All, thingClass, singleTyped)
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
          .valueQuery(projectIri, Some(hasPicture), iris, singleTyped)
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
    ),
  )
}
