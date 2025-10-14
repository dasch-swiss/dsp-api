/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import zio.*

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.search.FilterPattern
import org.knora.webapi.messages.util.search.OrderCriterion
import org.knora.webapi.messages.util.search.QueryPattern
import org.knora.webapi.messages.util.search.StatementPattern

trait WhereTransformer {

  /**
   * Optimises query patterns. Does not recurse. Must be called before `transformStatementInWhere`,
   * because optimisation might remove statements that would otherwise be expanded by `transformStatementInWhere`.
   *
   * @param patterns the query patterns to be optimised.
   * @return the optimised query patterns.
   */
  def optimiseQueryPatterns(patterns: Seq[QueryPattern]): Task[Seq[QueryPattern]]

  /**
   * Called before entering a UNION block.
   */
  def enteringUnionBlock(): Task[Unit]

  /**
   * Called before leaving a UNION block.
   */
  def leavingUnionBlock(): Task[Unit]

  /**
   * Transforms a [[StatementPattern]] in a WHERE clause into zero or more query patterns.
   *
   * @param statementPattern           the statement to be transformed.
   * @param inputOrderBy               the ORDER BY clause in the input query.
   * @param limitInferenceToOntologies a set of ontology IRIs, to which the simulated inference will be limited. If `None`, all possible inference will be done.
   * @return the result of the transformation.
   */
  def transformStatementInWhere(
    statementPattern: StatementPattern,
    inputOrderBy: Seq[OrderCriterion],
    limitInferenceToOntologies: Option[Set[SmartIri]] = None,
  ): Task[Seq[QueryPattern]]

  /**
   * Transforms a [[FilterPattern]] in a WHERE clause into zero or more query patterns.
   *
   * @param filterPattern the filter to be transformed.
   * @return the result of the transformation.
   */
  def transformFilter(filterPattern: FilterPattern): Task[Seq[QueryPattern]]
}
