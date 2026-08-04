/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
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
class ViewRestrictionsQuerySpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val thingClass = "http://www.knora.org/ontology/0001/anything#Thing"
  private val hasPicture = "http://www.knora.org/ontology/0001/anything#hasPicture"
  private val hidden     = Set("M knora-admin:ProjectMember", "V knora-admin:KnownUser")

  override def spec: Spec[TestEnvironment, Any] = suite("ViewRestrictionsRepo query generation")(
    suite("distinct permission literals")(
      test("resource variant selects only the distinct literal, restricted rows, excluding deleted") {
        val q = ViewRestrictionsRepo.distinctResourcePermissionsQuery(projectIri).getQueryString
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
        val q = ViewRestrictionsRepo.distinctValuePermissionsQuery(projectIri).getQueryString
        assertTrue(
          q.contains("SELECT DISTINCT ?permissions"),
          q.contains("FILTER NOT EXISTS") && q.contains("knora-base:LinkValue"),
          !q.contains("LIMIT"),
        )
      },
    ),
    suite("aggregated counts")(
      test("resource counts group by class and COUNT DISTINCT, filtered to the hidden literals") {
        val q = ViewRestrictionsRepo.resourceCountQuery(projectIri, hidden).getQueryString
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
          .valueCountQuery(projectIri, GroupBy.Property, ItemType.All, hidden)
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
          .valueCountQuery(projectIri, GroupBy.ResourceClass, ItemType.Comment, hidden)
          .getQueryString
        assertTrue(q.contains("knora-base:valueHasComment"))
      },
      test("itemType=Value excludes file values so File and Value cannot double-count") {
        val q = ViewRestrictionsRepo
          .valueCountQuery(projectIri, GroupBy.ResourceClass, ItemType.Value, hidden)
          .getQueryString
        assertTrue(q.contains("FILTER NOT EXISTS") && q.contains("knora-base:FileValue"))
      },
    ),
    suite("drill-down paging")(
      test("the page query orders deterministically and windows in SPARQL") {
        val q = ViewRestrictionsRepo
          .resourcePageQuery(projectIri, GroupBy.ResourceClass, ItemType.All, thingClass, offset = 50, limit = 25)
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
          .resourceCountForDrillDownQuery(projectIri, GroupBy.ResourceClass, ItemType.All, thingClass)
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
        val rq   = ViewRestrictionsRepo.resourceQuery(projectIri, Some(thingClass), iris).getQueryString
        val vq   = ViewRestrictionsRepo.valueQuery(projectIri, Some(hasPicture), GroupBy.Property, iris).getQueryString
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
          .resourcePageQuery(projectIri, GroupBy.Property, ItemType.All, hasPicture, offset = 0, limit = 25)
          .getQueryString
        assertTrue(q.contains(hasPicture))
      },
    ),
  )
}
