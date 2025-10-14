/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import zio.*

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*

import SparqlTransformer.*

class SelectTransformer(
  simulateInference: Boolean,
  inferencer: OntologyInferencer,
  mainRes: QueryVariable,
  implicit val stringFormatter: StringFormatter,
) extends WhereTransformer {

  override def enteringUnionBlock(): Task[Unit] = ZIO.unit

  override def leavingUnionBlock(): Task[Unit] = ZIO.unit

  override def transformFilter(filterPattern: FilterPattern): Task[Seq[QueryPattern]] = ZIO.succeed(Seq(filterPattern))

  override def transformStatementInWhere(
    statementPattern: StatementPattern,
    inputOrderBy: Seq[OrderCriterion],
    limitInferenceToOntologies: Option[Set[SmartIri]] = None,
  ): Task[Seq[QueryPattern]] =
    inferencer.transformStatementInWhere(
      statementPattern = statementPattern,
      simulateInference = simulateInference,
      limitInferenceToOntologies = limitInferenceToOntologies,
    )
  override def optimiseQueryPatterns(patterns: Seq[QueryPattern]): Task[Seq[QueryPattern]] = ZIO.attempt {
    moveBindToBeginning(optimiseIsDeletedWithFilter(moveLuceneToBeginning(patterns)))
  }

   * Specifies a FROM clause, if needed.
   *
   * @return the FROM clause to be used, if any.
   */
  def getFromClause: Task[Option[FromClause]] = ZIO.succeed(None)

  def getMainResourceVariable: QueryVariable = mainRes
}
