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
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ItemType

/**
 * Query-generator tests for [[ViewRestrictionsRepo]]. They assert on the rendered SPARQL string rather
 * than on triplestore results, pinning the query shape the service's correctness relies on: the
 * permission grouping the counts rely on, deleted exclusion, the file-value marker, and deterministic
 * ordering + windowing for the drill-down.
 *
 * The last suite pins the whole shape rather than single features: every query is compared against the
 * RDF4J SparqlBuilder output it was ported from (see [[ViewRestrictionsLegacyFixtures]]). The port is a
 * pure port, so the comparison is the strict one the rest of this migration uses — both sides parsed by
 * Jena with the prefix map cleared and re-serialised, which ignores whitespace and prefix rendering but
 * nothing else, group nesting included. The algebra of both is asserted alongside it, since that is what
 * join order and filter placement — the two things this report's performance rests on — actually reduce
 * to.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ViewRestrictionsQuerySpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val thingClass = "http://www.knora.org/ontology/0001/anything#Thing"
  private val bookClass  = "http://www.knora.org/ontology/0001/anything#Book"
  private val hasPicture = "http://www.knora.org/ontology/0001/anything#hasPicture"

  /** The common case: every resource carries exactly one class, so no most-specific-class filter is needed. */
  private val singleTyped = ViewRestrictionsRepo.ProjectClasses(Seq(thingClass), multiTyped = false)

  /** A project that asserts a class together with one of its ancestors — the filter must be kept. */
  private val multiTyped = ViewRestrictionsRepo.ProjectClasses(Seq(thingClass), multiTyped = true)

  /**
   * The `ProjectClasses` shapes the legacy comparison covers, keyed as
   * [[ViewRestrictionsLegacyFixtures]] keys them: the four combinations of "is there a `VALUES` clause"
   * and "is the most-specific-class filter needed", plus a two-IRI `VALUES` clause.
   */
  private val classesByKey = List(
    "single"    -> singleTyped,
    "multi"     -> multiTyped,
    "two"       -> ViewRestrictionsRepo.ProjectClasses(Seq(thingClass, bookClass), multiTyped = false),
    "none"      -> ViewRestrictionsRepo.ProjectClasses(Seq.empty, multiTyped = false),
    "noneMulti" -> ViewRestrictionsRepo.ProjectClasses(Seq.empty, multiTyped = true),
  )

  private val itemTypes = List(ItemType.All, ItemType.Resource, ItemType.File, ItemType.Value, ItemType.Comment)

  private val otherClasses = classesByKey.tail
  private val twoIris      = Seq("http://rdfh.ch/0001/a", "http://rdfh.ch/0001/b")
  private val oneIri       = Seq("http://rdfh.ch/0001/a")

  /** Parse and re-serialise, with prefixes expanded, so only the query itself is compared. */
  private def canonical(sparql: String): String = {
    val q = QueryFactory.create(sparql)
    q.getPrefixMapping.clearNsPrefixMap()
    q.toString
  }

  /** Unoptimised syntax-to-algebra translation: it keeps join order, group nesting and filter placement. */
  private def algebra(sparql: String): String = Algebra.compile(QueryFactory.create(sparql)).toString

  /** The ported queries, keyed exactly as [[ViewRestrictionsLegacyFixtures]] keys them. */
  private val actualByKey: Map[String, String] = (
    List(
      "projectClassesQuery"        -> ViewRestrictionsRepo.projectClassesQuery(projectIri).sparql,
      "anyMultiTypedResourceQuery" -> ViewRestrictionsRepo.anyMultiTypedResourceQuery(projectIri).sparql,
      "multiTypedQuery"            -> ViewRestrictionsRepo.multiTypedQuery(projectIri).sparql,
    ) ++ classesByKey.map { case (ck, cs) =>
      s"resourceCountsByClassAndPermissionQuery-$ck" ->
        ViewRestrictionsRepo.resourceCountsByClassAndPermissionQuery(projectIri, cs).sparql
    } ++ itemTypes.map { it =>
      s"valueCountsByPermissionQuery-$it-single" ->
        ViewRestrictionsRepo.valueCountsByPermissionQuery(projectIri, thingClass, it, singleTyped).sparql
    } ++ otherClasses.map { case (ck, cs) =>
      s"valueCountsByPermissionQuery-All-$ck" ->
        ViewRestrictionsRepo.valueCountsByPermissionQuery(projectIri, thingClass, ItemType.All, cs).sparql
    } ++ itemTypes.map { it =>
      s"resourcePageQuery-$it-single-50-25" ->
        ViewRestrictionsRepo.resourcePageQuery(projectIri, it, thingClass, 50, 25, singleTyped).sparql
    } ++ otherClasses.map { case (ck, cs) =>
      s"resourcePageQuery-All-$ck-50-25" ->
        ViewRestrictionsRepo.resourcePageQuery(projectIri, ItemType.All, thingClass, 50, 25, cs).sparql
    } ++ List(
      "resourcePageQuery-All-single-0-25" ->
        ViewRestrictionsRepo.resourcePageQuery(projectIri, ItemType.All, thingClass, 0, 25, singleTyped).sparql,
    ) ++ itemTypes.map { it =>
      s"resourceCountForDrillDownQuery-$it-single" ->
        ViewRestrictionsRepo.resourceCountForDrillDownQuery(projectIri, it, thingClass, singleTyped).sparql
    } ++ otherClasses.map { case (ck, cs) =>
      s"resourceCountForDrillDownQuery-All-$ck" ->
        ViewRestrictionsRepo.resourceCountForDrillDownQuery(projectIri, ItemType.All, thingClass, cs).sparql
    } ++ classesByKey.map { case (ck, cs) =>
      s"resourceQuery-group-$ck-two" ->
        ViewRestrictionsRepo.resourceQuery(projectIri, Some(thingClass), twoIris, cs).sparql
    } ++ List(
      "resourceQuery-noGroup-single-two" ->
        ViewRestrictionsRepo.resourceQuery(projectIri, None, twoIris, singleTyped).sparql,
      "resourceQuery-group-single-one" ->
        ViewRestrictionsRepo.resourceQuery(projectIri, Some(thingClass), oneIri, singleTyped).sparql,
    ) ++ classesByKey.map { case (ck, cs) =>
      s"valueQuery-group-$ck-two" ->
        ViewRestrictionsRepo.valueQuery(projectIri, Some(hasPicture), twoIris, cs).sparql
    } ++ List(
      "valueQuery-noGroup-single-two" ->
        ViewRestrictionsRepo.valueQuery(projectIri, None, twoIris, singleTyped).sparql,
      "valueQuery-group-single-one" ->
        ViewRestrictionsRepo.valueQuery(projectIri, Some(hasPicture), oneIri, singleTyped).sparql,
    )
  ).toMap

  override def spec: Spec[TestEnvironment, Any] = suite("ViewRestrictionsRepo query generation")(
    suite("permission-grouped counts (stepped report)")(
      test("resource counts group by class AND literal, with no permission filter at all") {
        val q = ViewRestrictionsRepo.resourceCountsByClassAndPermissionQuery(projectIri, singleTyped).sparql
        assertTrue(
          q.contains("COUNT") && q.contains("DISTINCT"),
          // Both axes in the grouping key: this is what lets one query answer every audience and both
          // restriction states at once.
          q.contains("GROUP BY ?resClass ?permissions"),
          // No literal IN-list — the absence of a permission filter is exactly what makes the class's
          // whole population derivable by summing its rows.
          !q.contains("M knora-admin:ProjectMember"),
          !q.contains("V knora-admin:KnownUser"),
          // The creator is neither projected nor constrained, so the join is dropped.
          !q.contains("?creator"),
          // exact at any size: no row cap
          !q.contains("LIMIT"),
        )
      },
      test("value counts group by literal alone and are narrowed to one class by FILTER, not by chunking") {
        val q =
          ViewRestrictionsRepo.valueCountsByPermissionQuery(projectIri, thingClass, ItemType.All, singleTyped).sparql
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
          .sparql
        val value = ViewRestrictionsRepo
          .valueCountsByPermissionQuery(projectIri, thingClass, ItemType.Value, singleTyped)
          .sparql
        assertTrue(
          comment.contains("knora-base:valueHasComment"),
          value.contains("FILTER NOT EXISTS") && value.contains("knora-base:FileValue"),
        )
      },
      test("the most-specific-class filter is kept, so a multi-typed resource is counted once") {
        // Load-bearing for the grouping: without it a resource asserted as several classes in one
        // hierarchy binds ?resClass once per class, double-counting it and overstating totalResources.
        val q = ViewRestrictionsRepo.resourceCountsByClassAndPermissionQuery(projectIri, multiTyped).sparql
        assertTrue(q.contains("rdfs:subClassOf+"))
      },
    ),
    suite("project class discovery")(
      test("the closure walk sits outside a sub-select, so it runs per class and not per resource row") {
        val q = ViewRestrictionsRepo.projectClassesQuery(projectIri).sparql
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
          ViewRestrictionsRepo.resourceCountsByClassAndPermissionQuery(projectIri, singleTyped).sparql,
          ViewRestrictionsRepo.valueCountsByPermissionQuery(projectIri, thingClass, ItemType.All, singleTyped).sparql,
          ViewRestrictionsRepo
            .resourcePageQuery(projectIri, ItemType.All, thingClass, offset = 0, limit = 25, singleTyped)
            .sparql,
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
          .sparql
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
        val q =
          ViewRestrictionsRepo.resourceCountForDrillDownQuery(projectIri, ItemType.All, thingClass, singleTyped).sparql
        assertTrue(
          q.contains("COUNT") && q.contains("DISTINCT"),
          q.contains(thingClass),
          // the count must span the whole result set, not one page
          !q.contains("LIMIT"),
          !q.contains("OFFSET"),
        )
      },
      test("row queries are scoped to the page's resource IRIs and carry no cap of their own") {
        val rq = ViewRestrictionsRepo.resourceQuery(projectIri, Some(thingClass), twoIris, singleTyped).sparql
        val vq = ViewRestrictionsRepo.valueQuery(projectIri, Some(hasPicture), twoIris, singleTyped).sparql
        assertTrue(
          twoIris.forall(rq.contains),
          twoIris.forall(vq.contains),
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
    suite("matches the RDF4J builder it was ported from")(
      ViewRestrictionsLegacyFixtures.byKey.toList.sortBy(_._1).map { case (key, legacy) =>
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
