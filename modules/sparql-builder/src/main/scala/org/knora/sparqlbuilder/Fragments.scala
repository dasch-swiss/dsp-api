/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

/** Combinators for building SPARQL graph patterns from fragments. */
object Fragments {

  /** Wrap a fragment in OPTIONAL { ... }. */
  def optional(body: Fragment): Fragment =
    sparql"OPTIONAL {\n  $body\n}"

  /** Wrap fragments in a UNION. */
  def union(branches: Fragment*): Fragment = {
    val joined = branches.map(b => sparql"{\n  $b\n}").reduce(_ ++ sparql" UNION " ++ _)
    joined
  }

  /** Wrap a fragment in GRAPH ... { ... }. Pass a fragment like `sparql"$myIri"` or `sparql"$myVar"`. */
  def graph(graphRef: Fragment)(body: Fragment): Fragment =
    sparql"GRAPH $graphRef {\n  $body\n}"

  /** Wrap a fragment in FILTER NOT EXISTS { ... }. */
  def filterNotExists(body: Fragment): Fragment =
    sparql"FILTER NOT EXISTS {\n  $body\n}"

  /** Wrap a fragment in MINUS { ... }. */
  def minus(body: Fragment): Fragment =
    sparql"MINUS {\n  $body\n}"

  /** Create a FILTER expression. */
  def filter(expr: Fragment): Fragment =
    sparql"FILTER($expr)"

  /** Create a BIND expression. */
  def bind(expr: Fragment, variable: Variable): Fragment =
    sparql"BIND($expr AS $variable)"

  /** Combine multiple optional fragments, discarding None values, with newline separation. */
  def combine(fragments: Option[Fragment]*): Fragment =
    Fragment.join(fragments.flatten, Fragment.raw("\n"))

  /** Create a VALUES clause. */
  def values(variable: Variable, iris: Iterable[Iri]): Fragment = {
    val iriList = iris.map(iri => sparql"$iri").reduce(_ ++ sparql" " ++ _)
    sparql"VALUES $variable { $iriList }"
  }

  /** Create a subquery (a SELECT embedded in a WHERE clause). */
  def subquery(query: Fragment): Fragment =
    sparql"{\n  $query\n}"
}
