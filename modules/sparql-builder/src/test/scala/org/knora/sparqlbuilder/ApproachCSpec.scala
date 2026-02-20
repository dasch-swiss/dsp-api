/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

import zio.test.*

/**
 * Approach C: Fluent immutable builder (no string interpolator)
 *
 * Instead of the `sparql"..."` interpolator, this approach uses method chaining
 * on immutable case classes to build queries. Each method returns a new copy.
 *
 * Key difference from Approach A: no string interpolator at all. Triple patterns
 * are built via `triple(s, p, o)` instead of `sparql"$s $p $o ."`.
 *
 * Key difference from Approach B: no explicit AST enum. Instead, builder methods
 * produce Fragments internally — the builder is the API, Fragment is the engine.
 */
object ApproachCSpec extends ZIOSpecDefault {

  // -- Fluent builder types (would live in a separate package in production) --

  /** A triple pattern built from typed values. */
  def triple(s: SparqlValue, p: SparqlValue, o: SparqlValue): Fragment =
    Fragment.fromParts(Vector(Fragment.ValuePart(s"${s.render} ${p.render} ${o.render} .")))

  /** An OPTIONAL block. */
  def optional(body: Fragment): Fragment =
    Fragment.fromParts(Vector(Fragment.RawPart(s"OPTIONAL {\n  ${body.render}\n}")))

  /** A UNION of branches. */
  def union(branches: Fragment*): Fragment = {
    val rendered = branches.map(b => s"{\n  ${b.render}\n}").mkString(" UNION ")
    Fragment.fromParts(Vector(Fragment.RawPart(rendered)))
  }

  /** FILTER NOT EXISTS block. */
  def filterNotExists(body: Fragment): Fragment =
    Fragment.fromParts(Vector(Fragment.RawPart(s"FILTER NOT EXISTS {\n  ${body.render}\n}")))

  /** A fluent SELECT query builder. */
  case class FluentSelect(
    variables: List[Variable] = Nil,
    distinct: Boolean = false,
    patterns: List[Fragment] = Nil,
    orderByClause: List[OrderBy] = Nil,
    limitValue: Option[Int] = None,
    offsetValue: Option[Int] = None,
    selectExprs: List[Fragment] = Nil,
  ) {
    def select(vars: Variable*): FluentSelect         = copy(variables = vars.toList)
    def selectDistinct(vars: Variable*): FluentSelect = copy(variables = vars.toList, distinct = true)
    def where(pats: Fragment*): FluentSelect          = copy(patterns = pats.toList)
    def orderBy(orderings: OrderBy*): FluentSelect    = copy(orderByClause = orderings.toList)
    def limit(n: Int): FluentSelect                   = copy(limitValue = Some(n))
    def offset(n: Int): FluentSelect                  = copy(offsetValue = Some(n))
    def withExpr(expr: Fragment): FluentSelect        = copy(selectExprs = selectExprs :+ expr)

    def render: String = {
      val sb = new StringBuilder
      sb.append("SELECT ")
      if (distinct) sb.append("DISTINCT ")
      val varList  = variables.map(_.render)
      val exprList = selectExprs.map(_.render)
      sb.append((varList ++ exprList).mkString(" "))
      sb.append("\nWHERE {\n")
      patterns.foreach(p => sb.append(s"  ${p.render}\n"))
      sb.append("}\n")
      if (orderByClause.nonEmpty)
        sb.append("ORDER BY " + orderByClause.map(_.render).mkString(" ") + "\n")
      limitValue.foreach(n => sb.append(s"LIMIT $n\n"))
      offsetValue.foreach(n => sb.append(s"OFFSET $n\n"))
      sb.result()
    }
  }

  /** A fluent ASK query builder. */
  case class FluentAsk(
    patterns: List[Fragment] = Nil,
  ) {
    def where(pats: Fragment*): FluentAsk = copy(patterns = pats.toList)

    def render: String = {
      val sb = new StringBuilder
      sb.append("ASK\nWHERE {\n")
      patterns.foreach(p => sb.append(s"  ${p.render}\n"))
      sb.append("}\n")
      sb.result()
    }
  }

  /** A fluent UPDATE query builder. */
  case class FluentUpdate(
    deleteClause: List[Fragment] = Nil,
    insertClause: List[Fragment] = Nil,
    whereClause: List[Fragment] = Nil,
  ) {
    def delete(pats: Fragment*): FluentUpdate = copy(deleteClause = pats.toList)
    def insert(pats: Fragment*): FluentUpdate = copy(insertClause = pats.toList)
    def where(pats: Fragment*): FluentUpdate  = copy(whereClause = pats.toList)

    def render: String = {
      val sb = new StringBuilder
      if (deleteClause.nonEmpty) {
        sb.append("DELETE {\n")
        deleteClause.foreach(p => sb.append(s"  ${p.render}\n"))
        sb.append("}\n")
      }
      if (insertClause.nonEmpty) {
        sb.append("INSERT {\n")
        insertClause.foreach(p => sb.append(s"  ${p.render}\n"))
        sb.append("}\n")
      }
      sb.append("WHERE {\n")
      whereClause.foreach(p => sb.append(s"  ${p.render}\n"))
      sb.append("}\n")
      sb.result()
    }
  }

  // -- Helpers --
  val knoraBase   = "http://www.knora.org/ontology/knora-base#"
  val rdfNs       = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  val rdfs        = "http://www.w3.org/2000/01/rdf-schema#"
  val kbIsDeleted = Iri.trusted(knoraBase + "isDeleted")
  val kbLastMod   = Iri.trusted(knoraBase + "lastModificationDate")
  val rdfType     = Iri.trusted(rdfNs + "type")

  override def spec = suite("Approach C: Fluent immutable builder")(
    simpleSelectSuite,
    isNodeUsedBenchmark,
    conditionalPatternsSuite,
    comparisonNote,
  )

  // -------------------------------------------------------------------------
  // Simple query: SELECT with OPTIONAL
  // -------------------------------------------------------------------------
  val simpleSelectSuite = suite("Simple SELECT with OPTIONAL")(
    test("renders a basic SELECT with OPTIONAL using fluent builder") {
      val s             = Variable("s")
      val p             = Variable("p")
      val o             = Variable("o")
      val lmd           = Variable("lastModDate")
      val resourceClass = Iri.trusted("http://example.org/MyClass")

      val query = FluentSelect()
        .select(s, p, o)
        .where(
          triple(s, rdfType, resourceClass),
          triple(s, kbIsDeleted, Literal.bool(false)),
          optional(triple(s, kbLastMod, lmd)),
          triple(s, p, o),
        )
        .orderBy(lmd.desc)
        .limit(25)
        .render

      assertTrue(
        query.contains("SELECT ?s ?p ?o"),
        query.contains("?s <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/MyClass>"),
        query.contains("OPTIONAL"),
        query.contains("LIMIT 25"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Benchmark: IsNodeUsedQuery (ASK with UNION)
  // -------------------------------------------------------------------------
  val isNodeUsedBenchmark = suite("Benchmark: IsNodeUsedQuery with fluent builder")(
    test("renders ASK with UNION") {
      val s              = Variable("s")
      val nodeIri        = Iri.trusted("http://rdfh.ch/lists/0001/treeList01")
      val guiAttr        = Iri.trusted("http://www.knora.org/ontology/salsah-gui#guiAttribute")
      val valHasListNode = Iri.trusted(knoraBase + "valueHasListNode")

      val query = FluentAsk()
        .where(
          union(
            triple(s, guiAttr, Literal.string(s"hlist=<${nodeIri.value}>")),
            triple(s, valHasListNode, nodeIri),
          ),
        )
        .render

      assertTrue(
        query.contains("ASK"),
        query.contains("UNION"),
        query.contains("guiAttribute"),
        query.contains("valueHasListNode"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Conditional patterns with fluent builder
  // -------------------------------------------------------------------------
  val conditionalPatternsSuite = suite("Conditional patterns")(
    test("Option-based patterns with fluent builder") {
      val s                            = Variable("s")
      val commentIri                   = Iri.trusted(knoraBase + "valueHasComment")
      val maybeComment: Option[String] = Some("A comment")

      val basePatterns    = List(triple(s, kbIsDeleted, Literal.bool(false)))
      val commentPatterns = maybeComment.toList.map(c => triple(s, commentIri, Literal.string(c)))

      val query = FluentSelect()
        .select(s)
        .where((basePatterns ++ commentPatterns)*)
        .render

      assertTrue(
        query.contains("isDeleted"),
        query.contains("valueHasComment"),
        query.contains("A comment"),
      )
    },
    test("iteration with indexed variables via fluent builder") {
      val resource = Variable("resource")
      val targets  = List("target1", "target2")
      val hasLink  = Iri.trusted(knoraBase + "hasLink")
      val refCount = Iri.trusted(knoraBase + "valueHasRefCount")

      val patterns = targets.zipWithIndex.flatMap { case (target, idx) =>
        val linkValue = Variable(s"linkValue$idx")
        val targetIri = Iri.trusted(s"http://example.org/$target")
        List(
          triple(resource, hasLink, targetIri),
          triple(linkValue, refCount, Literal.int(1)),
        )
      }

      val query = FluentSelect()
        .select(resource)
        .where(patterns*)
        .render

      assertTrue(
        query.contains("?linkValue0"),
        query.contains("?linkValue1"),
        query.contains("target1"),
        query.contains("target2"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Comparison note
  // -------------------------------------------------------------------------
  val comparisonNote = suite("Comparison notes")(
    test("fluent builder triple produces same output as interpolator") {
      val s = Variable("s")
      val p = Iri.trusted("http://example.org/prop")

      // Approach C (fluent builder):
      val fluentVersion = triple(s, p, Literal.bool(true))

      // Approach A (interpolator):
      val interpolatorVersion = sparql"$s $p ${Literal.bool(true)} ."

      assertTrue(
        fluentVersion.render == interpolatorVersion.render,
      )
    },
  )
}
