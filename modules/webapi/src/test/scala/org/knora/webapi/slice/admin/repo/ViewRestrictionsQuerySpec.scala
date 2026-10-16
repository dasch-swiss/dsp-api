/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.apache.jena.query.QueryFactory
import org.apache.jena.sparql.algebra.Algebra
import org.apache.jena.sparql.algebra.OpVisitorBase
import org.apache.jena.sparql.algebra.OpWalker
import org.apache.jena.sparql.algebra.op.OpBGP
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ValueCountRow
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ItemType
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

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
class ViewRestrictionsQuerySpec extends ZIOSpecDefault with GoldenTest {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")
  private val thingClass = "http://www.knora.org/ontology/0001/anything#Thing"
  private val hasPicture = "http://www.knora.org/ontology/0001/anything#hasPicture"
  private val hasText    = "http://www.knora.org/ontology/0001/anything#hasText"
  private val open       = "V knora-admin:UnknownUser"

  private val itemTypes = List(ItemType.All, ItemType.Resource, ItemType.File, ItemType.Value, ItemType.Comment)

  private val resourceLiterals = Set("M knora-admin:ProjectMember")
  private val valueLiterals    = Set("V knora-admin:KnownUser", "RV knora-admin:UnknownUser|V knora-admin:KnownUser")

  private val twoIris = Seq("http://rdfh.ch/0001/a", "http://rdfh.ch/0001/b")

  /** Parse and re-serialise, with prefixes expanded, so only the query itself is compared. */
  private def canonical(sparql: String): String = {
    val q = QueryFactory.create(sparql)
    q.getPrefixMapping.clearNsPrefixMap()
    q.toString
  }

  /** Unoptimised syntax-to-algebra translation: it keeps join order, group nesting and filter placement. */
  private def algebra(sparql: String): String = Algebra.compile(QueryFactory.create(sparql)).toString

  /** The ported queries, keyed exactly as [[ViewRestrictionsLegacyFixtures]] keys them. */
  private val actualByKey: Map[String, String] = Map(
    "projectClassesQuery"        -> ViewRestrictionsRepo.projectClassesQuery(projectIri).sparql,
    "anyMultiTypedResourceQuery" -> ViewRestrictionsRepo.anyMultiTypedResourceQuery(projectIri).sparql,
    "multiTypedQuery"            -> ViewRestrictionsRepo.multiTypedQuery(projectIri).sparql,
  )

  private def page(itemType: ItemType, resource: Set[String] = resourceLiterals, value: Set[String] = valueLiterals) =
    ViewRestrictionsRepo.resourcePageQuery(projectIri, itemType, thingClass, 50, 25, resource, value).sparql

  private def count(itemType: ItemType, resource: Set[String] = resourceLiterals, value: Set[String] = valueLiterals) =
    ViewRestrictionsRepo.resourceCountForDrillDownQuery(projectIri, itemType, thingClass, resource, value).sparql

  private def resourceRows =
    ViewRestrictionsRepo.resourceQuery(projectIri, thingClass, twoIris, resourceLiterals).sparql
  private def valueRows = ViewRestrictionsRepo.valueQuery(projectIri, hasPicture, twoIris, valueLiterals).sparql

  /** Every drill-down query shape: each item type, and each branch alone and together. */
  private val drillDownQueries: Map[String, String] =
    (itemTypes.flatMap(it => List(s"page-$it" -> page(it), s"count-$it" -> count(it))) ++ List(
      "page-All-resourcesOnly"  -> page(ItemType.All, value = Set.empty),
      "page-All-valuesOnly"     -> page(ItemType.All, resource = Set.empty),
      "count-All-resourcesOnly" -> count(ItemType.All, value = Set.empty),
      "count-All-valuesOnly"    -> count(ItemType.All, resource = Set.empty),
      "resourceQuery"           -> resourceRows,
      "valueQuery"              -> valueRows,
    )).toMap

  private val hasPermissionsIri = "http://www.knora.org/ontology/knora-base#hasPermissions"

  /**
   * Whether the optimised algebra of `sparql` has a basic-graph-pattern triple that matches a constant
   * `knora-base:hasPermissions` object. That is the shape Jena produces from `?permissions IN (...)` with
   * plain string literals, and it makes the store scan every object carrying that literal.
   */
  private def hasConstantPermissionTriple(sparql: String): Boolean = {
    val op      = Algebra.optimize(Algebra.compile(QueryFactory.create(sparql)))
    var found   = false
    val visitor = new OpVisitorBase {
      override def visit(opBGP: OpBGP): Unit =
        if (
          opBGP.getPattern.getList.stream
            .anyMatch(t => t.getPredicate.isURI && t.getPredicate.getURI == hasPermissionsIri && t.getObject.isConcrete)
        ) found = true
    }
    OpWalker.walk(op, visitor)
    found
  }

  override def spec: Spec[TestEnvironment, Any] = suite("ViewRestrictionsRepo query generation")(
    suite("class-scoped count queries")(
      test("resourceCounts") {
        val select = ViewRestrictionsRepo.resourceCountsQuery(projectIri)
        assertTrue(select.timeout == SparqlTimeout.ViewRestrictions) && assertGolden(select.sparql, "resourceCounts")
      },
      test("resourceListing") {
        val select = ViewRestrictionsRepo.resourceListingQuery(projectIri, thingClass)
        assertTrue(select.timeout == SparqlTimeout.ViewRestrictions) && assertGolden(select.sparql, "resourceListing")
      },
      test("valueCounts") {
        val select = ViewRestrictionsRepo.valueCountsQuery(projectIri, thingClass, twoIris)
        assertTrue(select.timeout == SparqlTimeout.ViewRestrictions) && assertGolden(select.sparql, "valueCounts")
      },
    ),
    suite("sumBatches")(
      test("rows with the same key add count and commented") {
        val rows = Seq(
          ValueCountRow(thingClass, hasText, open, deleted = false, count = 2, commented = 1),
          ValueCountRow(thingClass, hasText, open, deleted = false, count = 3, commented = 2),
        )
        assertTrue(
          ViewRestrictionsRepo.sumBatches(rows) ==
            Seq(ValueCountRow(thingClass, hasText, open, deleted = false, count = 5, commented = 3)),
        )
      },
      test("rows with different keys stay separate") {
        val rows = Seq(
          ValueCountRow(thingClass, hasText, open, deleted = false, count = 2, commented = 1),
          ValueCountRow(thingClass, hasText, open, deleted = true, count = 3, commented = 0),
          ValueCountRow(thingClass, hasText, "M knora-admin:ProjectMember", deleted = false, count = 1, commented = 0),
        )
        assertTrue(ViewRestrictionsRepo.sumBatches(rows).toSet == rows.toSet)
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
          ViewRestrictionsRepo.resourceCountsQuery(projectIri).sparql,
          ViewRestrictionsRepo.resourceListingQuery(projectIri, thingClass).sparql,
          ViewRestrictionsRepo.valueCountsQuery(projectIri, thingClass, twoIris).sparql,
          page(ItemType.All),
        )
        assertTrue(
          all.forall(_.contains(s"knora-base:attachedToProject <${projectIri.value}>")),
          all.forall(!_.contains("GRAPH")),
        )
      },
    ),
    suite("drill-down paging")(
      test("the page query orders deterministically and windows in SPARQL") {
        val q = page(ItemType.All)
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
        val q = count(ItemType.All)
        assertTrue(
          q.contains("COUNT") && q.contains("DISTINCT"),
          q.contains(thingClass),
          // the count must span the whole result set, not one page
          !q.contains("LIMIT"),
          !q.contains("OFFSET"),
        )
      },
      test("row queries are scoped to the page's resource IRIs and carry no cap of their own") {
        val rq = resourceRows
        val vq = valueRows
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
    suite("drill-down permission selection")(
      test("a literal with a quote and a backslash is escaped in the IN list") {
        val q = page(ItemType.Resource, resource = Set("""a"b\c"""))
        assertTrue(q.contains("""STR(?permissions) IN ("a\"b\\c")"""))
      },
      test(
        "an All page query carries the resource list in the resource branch and the value list in the value branch",
      ) {
        val Array(resourceBranch, valueBranch) = page(ItemType.All).split("UNION")
        assertTrue(
          resourceLiterals.forall(l => resourceBranch.contains(s""""$l"""")),
          valueLiterals.forall(l => !resourceBranch.contains(s""""$l"""")),
          valueLiterals.forall(l => valueBranch.contains(s""""$l"""")),
          resourceLiterals.forall(l => !valueBranch.contains(s""""$l"""")),
        )
      },
      test("an All count query carries one list per branch") {
        val Array(resourceBranch, valueBranch) = count(ItemType.All).split("UNION")
        assertTrue(
          resourceLiterals.forall(l => resourceBranch.contains(s""""$l"""")),
          valueLiterals.forall(l => valueBranch.contains(s""""$l"""")),
          valueLiterals.forall(l => !resourceBranch.contains(s""""$l"""")),
        )
      },
      test("a branch with an empty list is left out") {
        val valuesOnly    = page(ItemType.All, resource = Set.empty)
        val resourcesOnly = page(ItemType.All, value = Set.empty)
        assertTrue(
          !valuesOnly.contains("UNION"),
          valuesOnly.contains("knora-base:hasValue"),
          !resourcesOnly.contains("UNION"),
          !resourcesOnly.contains("knora-base:hasValue"),
        )
      },
      test("value branches skip standoff link values and no query tests for LinkValue") {
        assertTrue(
          drillDownQueries
            .filter(_._1.matches(".*(Value|All|Comment|File).*"))
            .values
            .forall(q => !q.contains("a knora-base:LinkValue")),
          page(ItemType.Value).contains("FILTER (?prop != knora-base:hasStandoffLinkToValue)"),
          valueRows.contains("FILTER (?prop != knora-base:hasStandoffLinkToValue)"),
        )
      },
      test("no drill-down query uses REGEX or the project class list") {
        assertTrue(drillDownQueries.values.forall(q => !q.contains("REGEX") && !q.contains("VALUES ?resClass")))
      },
      suite("the optimised algebra has no constant hasPermissions triple")(
        drillDownQueries.toList.sortBy(_._1).map { case (name, q) =>
          test(name)(assertTrue(!hasConstantPermissionTriple(q)))
        }*,
      ),
      test("the check catches a plain IN over the permission variable") {
        val plain =
          """PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
            |SELECT ?value WHERE {
            |  ?value knora-base:hasPermissions ?permissions .
            |  FILTER (?permissions IN ("M knora-admin:ProjectMember", "V knora-admin:KnownUser"))
            |}""".stripMargin
        assertTrue(hasConstantPermissionTriple(plain))
      },
    ),
    suite("matches the RDF4J builder it was ported from")(
      actualByKey.toList.sortBy(_._1).map { case (key, actual) =>
        test(key) {
          val legacy = ViewRestrictionsLegacyFixtures.byKey(key)
          assertTrue(
            canonical(actual) == canonical(legacy),
            algebra(actual) == algebra(legacy),
          )
        }
      }*,
    ),
  )
}
