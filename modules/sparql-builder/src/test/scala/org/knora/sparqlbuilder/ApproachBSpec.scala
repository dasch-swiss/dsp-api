/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

import zio.test.*

/**
 * Approach B: AST case classes with builder methods
 *
 * Instead of a string interpolator, this approach models SPARQL constructs as
 * explicit case classes forming an AST. The AST renders to SPARQL strings.
 *
 * Note: Approach B is demonstrated using the Approach A types (Iri, Variable, Literal)
 * and Fragment as the rendering backbone. This shows that both approaches can coexist —
 * the AST approach provides more structure, while the interpolator provides more flexibility.
 *
 * The key difference: Approach A treats SPARQL as text (Fragment), while Approach B
 * treats SPARQL as a structured tree (AST). In practice, the two can be combined —
 * AST nodes for well-known patterns, Fragment for the rest.
 */
object ApproachBSpec extends ZIOSpecDefault {

  // -- AST types (would live in a separate package in production) --

  /** A triple pattern: subject predicate object . */
  case class TriplePattern(subject: SparqlValue, predicate: SparqlValue, obj: SparqlValue) {
    def render: String       = s"${subject.render} ${predicate.render} ${obj.render} ."
    def toFragment: Fragment = Fragment.raw(render)
  }

  /** A graph pattern node in the AST. */
  enum GraphPattern {
    case Triple(pattern: TriplePattern)
    case Optional(patterns: List[GraphPattern])
    case Union(branches: List[List[GraphPattern]])
    case FilterNotExists(patterns: List[GraphPattern])
    case Minus(patterns: List[GraphPattern])
    case Filter(expr: String)
    case Bind(expr: String, variable: Variable)
    case Raw(fragment: Fragment) // escape hatch

    def render: String = this match {
      case Triple(p)    => p.render
      case Optional(ps) =>
        s"OPTIONAL {\n${ps.map(p => "  " + p.render).mkString("\n")}\n}"
      case Union(branches) =>
        branches.map(branch => s"{\n${branch.map(p => "  " + p.render).mkString("\n")}\n}").mkString(" UNION ")
      case FilterNotExists(ps) =>
        s"FILTER NOT EXISTS {\n${ps.map(p => "  " + p.render).mkString("\n")}\n}"
      case Minus(ps) =>
        s"MINUS {\n${ps.map(p => "  " + p.render).mkString("\n")}\n}"
      case Filter(expr)  => s"FILTER($expr)"
      case Bind(expr, v) => s"BIND($expr AS ${v.render})"
      case Raw(frag)     => frag.render
    }

    def toFragment: Fragment = Fragment.raw(render)
  }

  /** A SELECT query built from AST nodes. */
  case class AstSelect(
    variables: List[Variable],
    patterns: List[GraphPattern],
    orderBy: List[OrderBy] = Nil,
    limit: Option[Int] = None,
    offset: Option[Int] = None,
    distinct: Boolean = false,
  ) {
    def render: String = {
      val sb = new StringBuilder
      sb.append("SELECT ")
      if (distinct) sb.append("DISTINCT ")
      sb.append(variables.map(_.render).mkString(" "))
      sb.append("\nWHERE {\n")
      patterns.foreach(p => sb.append("  " + p.render + "\n"))
      sb.append("}\n")
      if (orderBy.nonEmpty)
        sb.append("ORDER BY " + orderBy.map(_.render).mkString(" ") + "\n")
      limit.foreach(n => sb.append(s"LIMIT $n\n"))
      offset.foreach(n => sb.append(s"OFFSET $n\n"))
      sb.result()
    }
  }

  /** An ASK query built from AST nodes. */
  case class AstAsk(patterns: List[GraphPattern]) {
    def render: String = {
      val sb = new StringBuilder
      sb.append("ASK\nWHERE {\n")
      patterns.foreach(p => sb.append("  " + p.render + "\n"))
      sb.append("}\n")
      sb.result()
    }
  }

  // -- Helpers --
  val knoraBase   = "http://www.knora.org/ontology/knora-base#"
  val rdfs        = "http://www.w3.org/2000/01/rdf-schema#"
  val kbIsDeleted = Iri.trusted(knoraBase + "isDeleted")
  val kbLastMod   = Iri.trusted(knoraBase + "lastModificationDate")

  def tp(s: SparqlValue, p: SparqlValue, o: SparqlValue): GraphPattern =
    GraphPattern.Triple(TriplePattern(s, p, o))

  override def spec = suite("Approach B: AST case classes")(
    simpleSelectSuite,
    isNodeUsedBenchmark,
    conditionalPatternsSuite,
    comparisonNote,
  )

  // -------------------------------------------------------------------------
  // Simple query: SELECT with OPTIONAL
  // -------------------------------------------------------------------------
  val simpleSelectSuite = suite("Simple SELECT with OPTIONAL")(
    test("renders a basic SELECT with OPTIONAL using AST") {
      val s             = Variable("s")
      val p             = Variable("p")
      val o             = Variable("o")
      val lmd           = Variable("lastModDate")
      val resourceClass = Iri.trusted("http://example.org/MyClass")

      val query = AstSelect(
        variables = List(s, p, o),
        patterns = List(
          tp(s, Iri.trusted("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), resourceClass),
          tp(s, kbIsDeleted, Literal.bool(false)),
          GraphPattern.Optional(List(tp(s, kbLastMod, lmd))),
          tp(s, p, o),
        ),
        orderBy = List(lmd.desc),
        limit = Some(25),
      ).render

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
  val isNodeUsedBenchmark = suite("Benchmark: IsNodeUsedQuery with AST")(
    test("renders ASK with UNION using AST") {
      val s              = Variable("s")
      val nodeIri        = Iri.trusted("http://rdfh.ch/lists/0001/treeList01")
      val guiAttr        = Iri.trusted("http://www.knora.org/ontology/salsah-gui#guiAttribute")
      val valHasListNode = Iri.trusted(knoraBase + "valueHasListNode")

      val query = AstAsk(
        patterns = List(
          GraphPattern.Union(
            List(
              List(tp(s, guiAttr, Literal.string(s"hlist=<${nodeIri.value}>"))),
              List(tp(s, valHasListNode, nodeIri)),
            ),
          ),
        ),
      ).render

      assertTrue(
        query.contains("ASK"),
        query.contains("UNION"),
        query.contains("guiAttribute"),
        query.contains("valueHasListNode"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Conditional patterns with AST
  // -------------------------------------------------------------------------
  val conditionalPatternsSuite = suite("Conditional patterns with AST")(
    test("Option-based patterns") {
      val s                            = Variable("s")
      val commentIri                   = Iri.trusted(knoraBase + "valueHasComment")
      val maybeComment: Option[String] = Some("A comment")

      val patterns: List[GraphPattern] =
        tp(s, Iri.trusted(knoraBase + "isDeleted"), Literal.bool(false)) ::
          maybeComment.toList.map(c => tp(s, commentIri, Literal.string(c)))

      val query = AstSelect(List(s), patterns).render

      assertTrue(
        query.contains("isDeleted"),
        query.contains("valueHasComment"),
        query.contains("A comment"),
      )
    },
    test("iteration with indexed variables via AST") {
      val resource = Variable("resource")
      val targets  = List("target1", "target2")
      val hasLink  = Iri.trusted(knoraBase + "hasLink")

      val patterns: List[GraphPattern] = targets.zipWithIndex.flatMap { case (target, idx) =>
        val linkValue = Variable(s"linkValue$idx")
        val targetIri = Iri.trusted(s"http://example.org/$target")
        List(
          tp(resource, hasLink, targetIri),
          tp(linkValue, Iri.trusted(knoraBase + "valueHasRefCount"), Literal.int(1)),
        )
      }

      val query = AstSelect(List(resource), patterns).render

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
    test("AST is more structured but more verbose") {
      // Approach A (Fragment):
      val fragVersion = {
        val s = Variable("s")
        val p = Iri.trusted("http://example.org/prop")
        sparql"$s $p ${Literal.bool(true)} ."
      }

      // Approach B (AST):
      val astVersion = {
        val s = Variable("s")
        val p = Iri.trusted("http://example.org/prop")
        TriplePattern(s, p, Literal.bool(true))
      }

      assertTrue(
        fragVersion.render == astVersion.render,
      )
    },
  )
}
