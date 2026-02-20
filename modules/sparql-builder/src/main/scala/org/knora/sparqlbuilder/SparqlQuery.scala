/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

/** Structured query types that accept Fragment values and render to complete SPARQL strings. */
object SparqlQuery {

  // ---------------------------------------------------------------------------
  // SELECT
  // ---------------------------------------------------------------------------

  def select(variables: Variable*): SelectBuilder =
    SelectBuilder(variables.toList, distinct = false)

  def selectDistinct(variables: Variable*): SelectBuilder =
    SelectBuilder(variables.toList, distinct = true)

  final case class SelectBuilder(
    variables: List[Variable],
    distinct: Boolean,
    whereClause: Fragment = Fragment.empty,
    orderByClause: List[OrderBy] = Nil,
    limitValue: Option[Int] = None,
    offsetValue: Option[Int] = None,
    groupByClause: List[Variable] = Nil,
    havingClause: Option[Fragment] = None,
    prefixes: List[(String, String)] = Nil,
    selectExprs: List[Fragment] = Nil,
  ) {

    def where(patterns: Fragment*): SelectBuilder =
      copy(whereClause = Fragment.join(patterns, Fragment.raw("\n")))

    def orderBy(orderings: OrderBy*): SelectBuilder =
      copy(orderByClause = orderings.toList)

    def limit(n: Int): SelectBuilder = copy(limitValue = Some(n))

    def offset(n: Int): SelectBuilder = copy(offsetValue = Some(n))

    def groupBy(vars: Variable*): SelectBuilder =
      copy(groupByClause = vars.toList)

    def having(expr: Fragment): SelectBuilder =
      copy(havingClause = Some(expr))

    def prefix(prefix: String, namespace: String): SelectBuilder =
      copy(prefixes = prefixes :+ (prefix, namespace))

    /** Add a computed expression to the SELECT clause (e.g., `COUNT(DISTINCT ?x) AS ?count`). */
    def withExpr(expr: Fragment): SelectBuilder =
      copy(selectExprs = selectExprs :+ expr)

    def render: String = {
      val sb = new StringBuilder

      // PREFIX declarations
      prefixes.foreach { case (p, ns) => sb.append(s"PREFIX $p: <$ns>\n") }
      if (prefixes.nonEmpty) sb.append("\n")

      // SELECT clause
      sb.append("SELECT ")
      if (distinct) sb.append("DISTINCT ")
      val varList  = variables.map(_.render)
      val exprList = selectExprs.map(_.render)
      sb.append((varList ++ exprList).mkString(" "))
      sb.append("\n")

      // WHERE clause
      sb.append("WHERE {\n")
      sb.append(indent(whereClause.render, 2))
      sb.append("\n}\n")

      // GROUP BY
      if (groupByClause.nonEmpty) {
        sb.append("GROUP BY ")
        sb.append(groupByClause.map(_.render).mkString(" "))
        sb.append("\n")
      }

      // HAVING
      havingClause.foreach { h =>
        sb.append(s"HAVING (${h.render})\n")
      }

      // ORDER BY
      if (orderByClause.nonEmpty) {
        sb.append("ORDER BY ")
        sb.append(orderByClause.map(_.render).mkString(" "))
        sb.append("\n")
      }

      // LIMIT / OFFSET
      limitValue.foreach(n => sb.append(s"LIMIT $n\n"))
      offsetValue.foreach(n => sb.append(s"OFFSET $n\n"))

      sb.result()
    }
  }

  // ---------------------------------------------------------------------------
  // CONSTRUCT
  // ---------------------------------------------------------------------------

  def construct(template: Fragment): ConstructBuilder =
    ConstructBuilder(template)

  final case class ConstructBuilder(
    template: Fragment,
    whereClause: Fragment = Fragment.empty,
    prefixes: List[(String, String)] = Nil,
  ) {
    def where(patterns: Fragment*): ConstructBuilder =
      copy(whereClause = Fragment.join(patterns, Fragment.raw("\n")))

    def prefix(prefix: String, namespace: String): ConstructBuilder =
      copy(prefixes = prefixes :+ (prefix, namespace))

    def render: String = {
      val sb = new StringBuilder
      prefixes.foreach { case (p, ns) => sb.append(s"PREFIX $p: <$ns>\n") }
      if (prefixes.nonEmpty) sb.append("\n")
      sb.append("CONSTRUCT {\n")
      sb.append(indent(template.render, 2))
      sb.append("\n}\n")
      sb.append("WHERE {\n")
      sb.append(indent(whereClause.render, 2))
      sb.append("\n}\n")
      sb.result()
    }
  }

  // ---------------------------------------------------------------------------
  // ASK
  // ---------------------------------------------------------------------------

  def ask: AskBuilder = AskBuilder()

  final case class AskBuilder(
    whereClause: Fragment = Fragment.empty,
    prefixes: List[(String, String)] = Nil,
  ) {
    def where(patterns: Fragment*): AskBuilder =
      copy(whereClause = Fragment.join(patterns, Fragment.raw("\n")))

    def prefix(prefix: String, namespace: String): AskBuilder =
      copy(prefixes = prefixes :+ (prefix, namespace))

    def render: String = {
      val sb = new StringBuilder
      prefixes.foreach { case (p, ns) => sb.append(s"PREFIX $p: <$ns>\n") }
      if (prefixes.nonEmpty) sb.append("\n")
      sb.append("ASK\n")
      sb.append("WHERE {\n")
      sb.append(indent(whereClause.render, 2))
      sb.append("\n}\n")
      sb.result()
    }
  }

  // ---------------------------------------------------------------------------
  // UPDATE (DELETE/INSERT WHERE)
  // ---------------------------------------------------------------------------

  def update: UpdateBuilder = UpdateBuilder()

  final case class UpdateBuilder(
    deleteClause: Fragment = Fragment.empty,
    insertClause: Fragment = Fragment.empty,
    whereClause: Fragment = Fragment.empty,
    fromGraph: Option[Interpolatable] = None,
    intoGraph: Option[Interpolatable] = None,
    prefixes: List[(String, String)] = Nil,
  ) {
    def delete(patterns: Fragment*): UpdateBuilder =
      copy(deleteClause = Fragment.join(patterns, Fragment.raw("\n")))

    def insert(patterns: Fragment*): UpdateBuilder =
      copy(insertClause = Fragment.join(patterns, Fragment.raw("\n")))

    def where(patterns: Fragment*): UpdateBuilder =
      copy(whereClause = Fragment.join(patterns, Fragment.raw("\n")))

    def from(graph: Interpolatable): UpdateBuilder = copy(fromGraph = Some(graph))

    def into(graph: Interpolatable): UpdateBuilder = copy(intoGraph = Some(graph))

    def prefix(prefix: String, namespace: String): UpdateBuilder =
      copy(prefixes = prefixes :+ (prefix, namespace))

    def render: String = {
      val sb = new StringBuilder
      prefixes.foreach { case (p, ns) => sb.append(s"PREFIX $p: <$ns>\n") }
      if (prefixes.nonEmpty) sb.append("\n")

      // DELETE clause
      if (deleteClause != Fragment.empty) {
        sb.append("DELETE {\n")
        fromGraph.foreach { g =>
          sb.append(s"  GRAPH ${renderInterpolatable(g)} {\n")
        }
        sb.append(indent(deleteClause.render, if (fromGraph.isDefined) 4 else 2))
        sb.append("\n")
        fromGraph.foreach(_ => sb.append("  }\n"))
        sb.append("}\n")
      }

      // INSERT clause
      if (insertClause != Fragment.empty) {
        sb.append("INSERT {\n")
        intoGraph.foreach { g =>
          sb.append(s"  GRAPH ${renderInterpolatable(g)} {\n")
        }
        sb.append(indent(insertClause.render, if (intoGraph.isDefined) 4 else 2))
        sb.append("\n")
        intoGraph.foreach(_ => sb.append("  }\n"))
        sb.append("}\n")
      }

      // WHERE clause
      sb.append("WHERE {\n")
      sb.append(indent(whereClause.render, 2))
      sb.append("\n}\n")

      sb.result()
    }
  }

  // ---------------------------------------------------------------------------
  // INSERT DATA
  // ---------------------------------------------------------------------------

  def insertData: InsertDataBuilder = InsertDataBuilder()

  final case class InsertDataBuilder(
    dataClause: Fragment = Fragment.empty,
    intoGraph: Option[Iri] = None,
    prefixes: List[(String, String)] = Nil,
  ) {
    def data(patterns: Fragment*): InsertDataBuilder =
      copy(dataClause = Fragment.join(patterns, Fragment.raw("\n")))

    def into(graph: Iri): InsertDataBuilder = copy(intoGraph = Some(graph))

    def prefix(prefix: String, namespace: String): InsertDataBuilder =
      copy(prefixes = prefixes :+ (prefix, namespace))

    def render: String = {
      val sb = new StringBuilder
      prefixes.foreach { case (p, ns) => sb.append(s"PREFIX $p: <$ns>\n") }
      if (prefixes.nonEmpty) sb.append("\n")
      sb.append("INSERT DATA {\n")
      intoGraph.foreach(g => sb.append(s"  GRAPH <$g> {\n"))
      sb.append(indent(dataClause.render, if (intoGraph.isDefined) 4 else 2))
      sb.append("\n")
      intoGraph.foreach(_ => sb.append("  }\n"))
      sb.append("}\n")
      sb.result()
    }
  }

  // ---------------------------------------------------------------------------
  // Graph management
  // ---------------------------------------------------------------------------

  def dropGraph(graph: Iri): String = s"DROP GRAPH <$graph>"

  def clearGraph(graph: Iri): String = s"CLEAR GRAPH <$graph>"

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private def indent(s: String, spaces: Int): String = {
    val prefix = " " * spaces
    s.linesIterator.map(line => if (line.isBlank) line else prefix + line).mkString("\n")
  }

  private def renderInterpolatable(v: Interpolatable): String = v match {
    case sv: SparqlValue => sv.render
    case frag: Fragment  => frag.render
  }
}
