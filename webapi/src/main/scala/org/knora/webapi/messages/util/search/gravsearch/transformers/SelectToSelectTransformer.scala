package org.knora.webapi.messages.util.search.gravsearch.transformers

import zio._

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.v2.responder.searchmessages.LimitInference

import SparqlTransformer._

class SelectToSelectTransformer(
  sparqlTransformerLive: SparqlTransformerLive,
  implicit val stringFormatter: StringFormatter
) extends WhereTransformer {

  /**
   * Transforms a [[StatementPattern]] in a SELECT's WHERE clause into zero or more statement patterns.
   *
   * @param statementPattern the statement to be transformed.
   * @return the result of the transformation.
   */
  def transformStatementInSelect(statementPattern: StatementPattern): Task[Seq[StatementPattern]] =
    ZIO.succeed(Seq(statementPattern))

  override def transformStatementInWhere(
    statementPattern: StatementPattern,
    inputOrderBy: Seq[OrderCriterion],
    inference: LimitInference
  ): Task[Seq[QueryPattern]] =
    sparqlTransformerLive.transformStatementInWhereForNoInference(
      statementPattern = statementPattern,
      inference = inference
    )
  override def optimiseQueryPatterns(patterns: Seq[QueryPattern]): Task[Seq[QueryPattern]] = ZIO.attempt {
    moveBindToBeginning(optimiseIsDeletedWithFilter(moveLuceneToBeginning(patterns)))
  }

  override def transformLuceneQueryPattern(luceneQueryPattern: LuceneQueryPattern): Task[Seq[QueryPattern]] =
    sparqlTransformerLive.transformLuceneQueryPatternForFuseki(luceneQueryPattern)

  /**
   * Specifies a FROM clause, if needed.
   *
   * @return the FROM clause to be used, if any.
   */
  def getFromClause: Task[Option[FromClause]] = ZIO.succeed(None)
}
