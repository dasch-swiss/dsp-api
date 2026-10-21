/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.slice.common.domain.InternalIri

/**
 * Lets [[AbstractPrequeryGenerator]] wrap the Lucene statement of a lookup anchored on the main resource in
 * `GRAPH <dataGraph> { }` (`docs/adr/0012-graph-scoped-lucene-text-lookups.md`). Set only when the query is restricted
 * to a project whose data graph is known; the caller keeps the main resource's `attachedToProject` either way.
 *
 * @param dataGraph  the data graph of the project the query is restricted to.
 * @param inputWhere the input WHERE patterns, type annotations removed. `matchFulltext` and `matchLabel` are anchored
 *                   on the main resource by their resource variable alone; a value-anchored lookup (`matchText`,
 *                   `matchTextInStandoff`) needs the statements that bind its value, taken from these patterns.
 */
final case class LuceneScope(dataGraph: InternalIri, inputWhere: Seq[QueryPattern])

object LuceneScope {

  /**
   * The value variables a `matchText` / `matchTextInStandoff` lookup may scope: each is the object of at least one
   * statement, every statement with it as object has `mainResource` as subject, and none has `rdfs:label` (a label is
   * a literal, not a value). Any other binding, in any OPTIONAL, UNION, MINUS or FILTER NOT EXISTS, may reach a linked
   * resource in another project's graph, so it disqualifies the variable.
   *
   * Computed over the whole input rather than while generating, which visits a FILTER before the statements of a
   * later block and would miss a binding that follows it.
   */
  def mainResourceAnchoredValues(inputWhere: Seq[QueryPattern], mainResource: QueryVariable): Set[QueryVariable] = {
    def statements(patterns: Seq[QueryPattern]): Seq[StatementPattern] = patterns.flatMap {
      case statement: StatementPattern    => Seq(statement)
      case OptionalPattern(nested)        => statements(nested)
      case MinusPattern(nested)           => statements(nested)
      case FilterNotExistsPattern(nested) => statements(nested)
      case UnionPattern(blocks)           => blocks.flatMap(statements)
      case _                              => Seq.empty
    }

    def anchored(statement: StatementPattern): Boolean = statement match {
      case StatementPattern(_, IriRef(pred, _), _) if pred.toIri == OntologyConstants.Rdfs.Label => false
      case StatementPattern(subj, _, _)                                                          => subj == mainResource
    }

    statements(inputWhere).collect { case statement @ StatementPattern(_, _, obj: QueryVariable) => obj -> statement }
      .groupMap(_._1)(_._2)
      .collect { case (variable, bindings) if bindings.forall(anchored) => variable }
      .toSet
  }
}
