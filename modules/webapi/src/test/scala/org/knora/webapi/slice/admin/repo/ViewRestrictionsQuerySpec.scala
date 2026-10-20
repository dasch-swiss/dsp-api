/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import org.apache.jena.graph.Triple
import org.apache.jena.query.QueryFactory
import org.apache.jena.sparql.algebra.Algebra
import org.apache.jena.sparql.algebra.OpVisitorBase
import org.apache.jena.sparql.algebra.OpWalker
import org.apache.jena.sparql.algebra.op.OpBGP
import org.apache.jena.sparql.algebra.op.OpConditional
import org.apache.jena.sparql.algebra.op.OpJoin
import org.apache.jena.sparql.algebra.op.OpLeftJoin
import org.junit.runner.RunWith
import zio.test.*

import org.knora.sparqlbuilder.Iri
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.GoldenTest
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.DrillDownBranches
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.DrillDownBranches.Both
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.DrillDownBranches.ResourcesOnly
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.DrillDownBranches.ValuesOnly
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ItemType
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout

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
  private val graph      = Iri.unsafeFrom("http://www.knora.org/data/0001/anything")

  private val itemTypes = List(ItemType.All, ItemType.Resource, ItemType.File, ItemType.Value, ItemType.Comment)

  private val resourceLiterals = Set("M knora-admin:ProjectMember")
  private val valueLiterals    = Set("V knora-admin:KnownUser", "RV knora-admin:UnknownUser|V knora-admin:KnownUser")

  private val twoIris = Seq("http://rdfh.ch/0001/a", "http://rdfh.ch/0001/b")

  private def branches(itemType: ItemType, resource: Set[String], value: Set[String]): DrillDownBranches =
    DrillDownBranches.of(itemType, resource, value).get

  private def page(itemType: ItemType, resource: Set[String] = resourceLiterals, value: Set[String] = valueLiterals) =
    ViewRestrictionsRepo
      .resourcePageQuery(graph, itemType, thingClass, 50, 25, branches(itemType, resource, value))
      .sparql

  private def count(itemType: ItemType, resource: Set[String] = resourceLiterals, value: Set[String] = valueLiterals) =
    ViewRestrictionsRepo
      .resourceCountForDrillDownQuery(graph, itemType, thingClass, branches(itemType, resource, value))
      .sparql

  private def resourceRows =
    ViewRestrictionsRepo.resourceQuery(projectIri, thingClass, twoIris, resourceLiterals).sparql
  private def valueRows = ViewRestrictionsRepo.valueQuery(projectIri, hasPicture, twoIris, valueLiterals).sparql

  /** Every page and count query shape: each item type, and each branch alone and together. */
  private val pageAndCountQueries: Map[String, String] =
    (itemTypes.flatMap(it => List(s"page-$it" -> page(it), s"count-$it" -> count(it))) ++ List(
      "page-All-resourcesOnly"  -> page(ItemType.All, value = Set.empty),
      "page-All-valuesOnly"     -> page(ItemType.All, resource = Set.empty),
      "count-All-resourcesOnly" -> count(ItemType.All, value = Set.empty),
      "count-All-valuesOnly"    -> count(ItemType.All, resource = Set.empty),
    )).toMap

  /** Every drill-down query shape: each item type, and each branch alone and together. */
  private val drillDownQueries: Map[String, String] =
    pageAndCountQueries ++ Map("resourceQuery" -> resourceRows, "valueQuery" -> valueRows)

  private val goldenLiterals =
    Set("M knora-admin:ProjectMember", "V knora-admin:KnownUser|RV \"q\" knora-admin:UnknownUser")

  private val goldenQueries: Map[String, String] = Map(
    "resourcePage-Resource"  -> page(ItemType.Resource, goldenLiterals, goldenLiterals),
    "resourcePage-Value"     -> page(ItemType.Value, goldenLiterals, goldenLiterals),
    "resourcePage-All"       -> page(ItemType.All, goldenLiterals, goldenLiterals),
    "resourceCount-Resource" -> count(ItemType.Resource, goldenLiterals, goldenLiterals),
    "resourceCount-Value"    -> count(ItemType.Value, goldenLiterals, goldenLiterals),
    "resourceCount-All"      -> count(ItemType.All, goldenLiterals, goldenLiterals),
    "resourceQuery"          -> ViewRestrictionsRepo.resourceQuery(projectIri, thingClass, twoIris, goldenLiterals).sparql,
    "valueQuery"             -> ViewRestrictionsRepo.valueQuery(projectIri, thingClass, twoIris, goldenLiterals).sparql,
  )

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

  private def optimised(sparql: String) = Algebra.optimize(Algebra.compile(QueryFactory.create(sparql)))

  /** The triples of every basic graph pattern in the optimised algebra of `sparql`. */
  private def bgpTriples(sparql: String): List[Triple] = {
    val triples = List.newBuilder[Triple]
    OpWalker.walk(
      optimised(sparql),
      new OpVisitorBase {
        override def visit(opBGP: OpBGP): Unit = opBGP.getPattern.getList.forEach(t => triples += t)
      },
    )
    triples.result()
  }

  /** Whether the optimised algebra of `sparql` has an `OPTIONAL`, which Jena evaluates once per input row. */
  private def hasOptional(sparql: String): Boolean = {
    var found = false
    OpWalker.walk(
      optimised(sparql),
      new OpVisitorBase {
        override def visit(op: OpLeftJoin): Unit    = found = true
        override def visit(op: OpConditional): Unit = found = true
      },
    )
    found
  }

  private val isDeletedIri = "http://www.knora.org/ontology/knora-base#isDeleted"

  /**
   * Whether the optimised algebra of `sparql` joins two independently evaluated parts. The drill-down's barrier
   * sub-select must be joined as a sequence, so the patterns after it run with its bindings; as a plain join they
   * would scan the store on their own.
   */
  private def hasIndependentJoin(sparql: String): Boolean = {
    var found = false
    OpWalker.walk(
      optimised(sparql),
      new OpVisitorBase {
        override def visit(op: OpJoin): Unit = found = true
      },
    )
    found
  }

  override def spec: Spec[TestEnvironment, Any] = suite("ViewRestrictionsRepo query generation")(
    suite("count queries")(
      test("resourceCounts") {
        val select = ViewRestrictionsRepo.resourceCountsQuery(projectIri)
        assertTrue(select.timeout == SparqlTimeout.ViewRestrictions) && assertGolden(select.sparql, "resourceCounts")
      },
      test("valueCounts") {
        val select = ViewRestrictionsRepo.valueCountsQuery(graph)
        assertTrue(select.timeout == SparqlTimeout.ViewRestrictionsCounts) && assertGolden(select.sparql, "valueCounts")
      },
      test("valueCounts has one constant-object pattern, the isDeleted false driver") {
        val constant = bgpTriples(ViewRestrictionsRepo.valueCountsQuery(graph).sparql).filter(_.getObject.isConcrete)
        assertTrue(
          constant.size == 1,
          constant.forall(t => t.getPredicate.getURI == isDeletedIri && t.getObject.getLiteralLexicalForm == "false"),
        )
      },
      test("valueCounts has no OPTIONAL") {
        assertTrue(!hasOptional(ViewRestrictionsRepo.valueCountsQuery(graph).sparql))
      },
      test("valueCounts scopes the scan to the given graph") {
        val q = ViewRestrictionsRepo.valueCountsQuery(graph).sparql
        assertTrue(q.contains("GRAPH <http://www.knora.org/data/0001/anything>"), !q.contains("attachedToProject"))
      },
    ),
    suite("DrillDownBranches.of")(
      test("Resource keeps the resource literals only") {
        assertTrue(
          DrillDownBranches.of(ItemType.Resource, resourceLiterals, valueLiterals) ==
            Some(ResourcesOnly(resourceLiterals)),
        )
      },
      test("File, Value and Comment keep the value literals only") {
        assertTrue(
          List(ItemType.File, ItemType.Value, ItemType.Comment).forall(it =>
            DrillDownBranches.of(it, resourceLiterals, valueLiterals) == Some(ValuesOnly(valueLiterals)),
          ),
        )
      },
      test("All keeps both") {
        assertTrue(
          DrillDownBranches.of(ItemType.All, resourceLiterals, valueLiterals) ==
            Some(Both(resourceLiterals, valueLiterals)),
        )
      },
      test("a branch with an empty literal set is absent") {
        assertTrue(
          DrillDownBranches.of(ItemType.All, resourceLiterals, Set.empty) == Some(ResourcesOnly(resourceLiterals)),
          DrillDownBranches.of(ItemType.All, Set.empty, valueLiterals) == Some(ValuesOnly(valueLiterals)),
        )
      },
      test("is None when the item type needs a branch only the other literals could fill") {
        assertTrue(
          DrillDownBranches.of(ItemType.Resource, Set.empty, valueLiterals).isEmpty,
          DrillDownBranches.of(ItemType.Value, resourceLiterals, Set.empty).isEmpty,
          DrillDownBranches.of(ItemType.All, Set.empty, Set.empty).isEmpty,
        )
      },
    ),
    suite("drill-down golden queries")(
      test("resourcePage-Resource")(assertGolden(goldenQueries("resourcePage-Resource"), "resourcePage-Resource")),
      test("resourcePage-Value")(assertGolden(goldenQueries("resourcePage-Value"), "resourcePage-Value")),
      test("resourcePage-All")(assertGolden(goldenQueries("resourcePage-All"), "resourcePage-All")),
      test("resourceCount-Resource")(assertGolden(goldenQueries("resourceCount-Resource"), "resourceCount-Resource")),
      test("resourceCount-Value")(assertGolden(goldenQueries("resourceCount-Value"), "resourceCount-Value")),
      test("resourceCount-All")(assertGolden(goldenQueries("resourceCount-All"), "resourceCount-All")),
      test("resourceQuery")(assertGolden(goldenQueries("resourceQuery"), "resourceQuery")),
      test("valueQuery")(assertGolden(goldenQueries("valueQuery"), "valueQuery")),
    ),
    suite("graph scoping")(
      test("the resource counts keep the attachedToProject join") {
        assertTrue(
          ViewRestrictionsRepo
            .resourceCountsQuery(projectIri)
            .sparql
            .contains(s"knora-base:attachedToProject <${projectIri.value}>"),
        )
      },
      test("every page and count query is scoped to the given graph instead of attachedToProject") {
        assertTrue(
          pageAndCountQueries.values.forall(_.contains("GRAPH <http://www.knora.org/data/0001/anything>")),
          pageAndCountQueries.values.forall(!_.contains("attachedToProject")),
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
          // `VALUES` opens the WHERE, so the page's resources anchor the query
          rq.contains("WHERE {\n  VALUES ?resource {"),
          vq.contains("WHERE {\n  VALUES ?resource {"),
          !rq.contains("?resource IN"),
          !vq.contains("?resource IN"),
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
        val q = ViewRestrictionsRepo.resourceQuery(projectIri, thingClass, twoIris, Set("""a"b\c""")).sparql
        assertTrue(q.contains("""STR(?permissions) IN ("a\"b\\c")"""))
      },
      test("a literal with a quote and a backslash is escaped in the VALUES list") {
        val q = page(ItemType.Resource, resource = Set("""a"b\c"""))
        assertTrue(q.contains("""VALUES ?permissions { "a\"b\\c" }"""))
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
      test("every class value branch skips the standoff link value") {
        val filter = "FILTER (?prop != knora-base:hasStandoffLinkToValue)"
        assertTrue(
          page(ItemType.Value).contains(filter),
          count(ItemType.Value).contains(filter),
          page(ItemType.All).split("UNION")(1).contains(filter),
          count(ItemType.All).split("UNION")(1).contains(filter),
          valueRows.contains(filter),
        )
      },
      test("the row queries bind the class as ?resClass") {
        assertTrue(
          resourceRows.contains(s"BIND(<$thingClass> AS ?resClass)"),
          valueRows.contains(s"BIND(<$hasPicture> AS ?resClass)"),
        )
      },
      test("no drill-down query uses REGEX or the project class list") {
        assertTrue(drillDownQueries.values.forall(q => !q.contains("REGEX") && !q.contains("VALUES ?resClass")))
      },
      suite("the optimised algebra of a row query has no constant hasPermissions triple")(
        List("resourceQuery" -> resourceRows, "valueQuery" -> valueRows).map { case (name, q) =>
          test(name)(assertTrue(!hasConstantPermissionTriple(q)))
        }*,
      ),
      suite("the page and count queries select the objects by a VALUES list behind a sub-select barrier")(
        pageAndCountQueries.toList.sortBy(_._1).map { case (name, q) =>
          test(name)(
            assertTrue(
              q.contains("VALUES ?permissions {"),
              !q.contains("STR(?permissions)"),
              !hasIndependentJoin(q),
            ),
          )
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
  )
}
