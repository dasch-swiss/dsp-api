package org.knora.webapi.messages.util.search.gravsearch.transformers

import zio._

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search._

import SparqlTransformer._

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

  override def transformFilter(filterPattern: FilterPattern): ZIO[Any, Nothing, Seq[FilterPattern]] =
    ZIO.succeed(Seq(filterPattern))

  override def optimiseQueryPatterns(patterns: Seq[QueryPattern]): Task[Seq[QueryPattern]] =
    ZIO.attempt(moveBindToBeginning(optimiseIsDeletedWithFilter(moveLuceneToBeginning(patterns))))

  override def transformLuceneQueryPattern(luceneQueryPattern: LuceneQueryPattern): Task[Seq[QueryPattern]] =
    sparqlTransformerLive.transformLuceneQueryPatternForFuseki(luceneQueryPattern)

  override def enteringUnionBlock(): UIO[Unit] = ZIO.unit

  override def leavingUnionBlock(): UIO[Unit] = ZIO.unit
}
