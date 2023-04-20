/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch

import zio._

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.LuceneQueryPattern
import org.knora.webapi.messages.util.search.OrderCriterion
import org.knora.webapi.messages.util.search.QueryPattern
import org.knora.webapi.messages.util.search.SparqlTransformer._
import org.knora.webapi.messages.util.search.SparqlTransformerLive
import org.knora.webapi.messages.util.search.StatementPattern
import org.knora.webapi.messages.util.search.WhereTransformer

class ConstructToConstructTransformer(
  sparqlTransformerLive: SparqlTransformerLive,
  implicit val stringFormatter: StringFormatter
) extends WhereTransformer {

  /**
   * Transforms a [[StatementPattern]] in a CONSTRUCT clause into zero or more statement patterns.
   *
   * @param statementPattern the statement to be transformed.
   * @return the result of the transformation.
   */
  def transformStatementInConstruct(statementPattern: StatementPattern): Task[Seq[StatementPattern]] =
    ZIO.succeed(Seq(statementPattern))

  override def transformStatementInWhere(
    statementPattern: StatementPattern,
    inputOrderBy: Seq[OrderCriterion],
    limitInferenceToOntologies: Option[Set[SmartIri]] = None
  ): Task[Seq[QueryPattern]] =
    sparqlTransformerLive.transformStatementInWhereForNoInference(
      statementPattern = statementPattern,
      simulateInference = true,
      limitInferenceToOntologies = limitInferenceToOntologies
    )

  override def optimiseQueryPatterns(patterns: Seq[QueryPattern]): Task[Seq[QueryPattern]] =
    ZIO.attempt(moveBindToBeginning(optimiseIsDeletedWithFilter(moveLuceneToBeginning(patterns))))

  override def transformLuceneQueryPattern(luceneQueryPattern: LuceneQueryPattern): Task[Seq[QueryPattern]] =
    sparqlTransformerLive.transformLuceneQueryPatternForFuseki(luceneQueryPattern)

}
