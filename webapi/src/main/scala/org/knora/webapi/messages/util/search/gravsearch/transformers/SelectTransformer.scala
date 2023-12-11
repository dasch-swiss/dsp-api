/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
  sparqlTransformerLive: OntologyInferencer,
  implicit val stringFormatter: StringFormatter
) extends WhereTransformer {

  override def transformStatementInWhere(
    statementPattern: StatementPattern,
    inputOrderBy: Seq[OrderCriterion],
    limitInferenceToOntologies: Option[Set[SmartIri]] = None
  ): Task[Seq[QueryPattern]] =
    sparqlTransformerLive.transformStatementInWhere(
      statementPattern = statementPattern,
      simulateInference = simulateInference,
      limitInferenceToOntologies = limitInferenceToOntologies
    )
  override def optimiseQueryPatterns(patterns: Seq[QueryPattern]): Task[Seq[QueryPattern]] = ZIO.attempt {
    moveBindToBeginning(moveLuceneToBeginning(patterns))
  }

  /**
   * Specifies a FROM clause, if needed.
   *
   * @return the FROM clause to be used, if any.
   */
  def getFromClause: Task[Option[FromClause]] = ZIO.none
}
