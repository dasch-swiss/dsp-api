/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import zio._

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.search._
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

final case class ConstructToConstructTransformer(
  sparqlTransformerLive: SparqlTransformerLive,
  iriConverter: IriConverter
) {

  def transformStatementInWhere(
    statementPattern: StatementPattern,
    inputOrderBy: Seq[OrderCriterion],
    limitInferenceToOntologies: Option[Set[SmartIri]] = None
  ): Task[Seq[QueryPattern]] =
    sparqlTransformerLive.transformStatementInWhereForNoInference(
      statementPattern = statementPattern,
      simulateInference = true,
      limitInferenceToOntologies = limitInferenceToOntologies
    )

  private def transformLuceneQueryPattern(
    luceneQueryPattern: LuceneQueryPattern
  ): Task[Seq[QueryPattern]] =
    for {
      predIri  <- iriConverter.asSmartIri("http://jena.apache.org/text#query")
      datatype <- iriConverter.asSmartIri(OntologyConstants.Xsd.String)
      obj       = XsdLiteral(luceneQueryPattern.queryString.getQueryString, datatype)
    } yield Seq(StatementPattern(luceneQueryPattern.subj, IriRef(predIri), obj))

  /**
   * Traverses a CONSTRUCT query, delegating transformation tasks, and returns the transformed query.
   *
   * @param inputQuery                 the query to be transformed.
   * @param transformer                the [[ConstructToConstructTransformer]] to be used.
   * @param limitInferenceToOntologies a set of ontology IRIs, to which the simulated inference will be limited. If `None`, all possible inference will be done.
   * @return the transformed query.
   */
  def transformConstructToConstruct(
    inputQuery: ConstructQuery,
    limitInferenceToOntologies: Option[Set[SmartIri]] = None
  ): Task[ConstructQuery] = {

    def transformPattern(pattern: QueryPattern): Task[Seq[QueryPattern]] =
      pattern match {
        case statementPattern: StatementPattern =>
          transformStatementInWhere(
            statementPattern = statementPattern,
            inputOrderBy = inputQuery.orderBy,
            limitInferenceToOntologies = limitInferenceToOntologies
          )
        case FilterNotExistsPattern(patterns) => ZIO.foreach(patterns)(transformPattern(_).map(FilterNotExistsPattern))
        case MinusPattern(patterns)           => ZIO.foreach(patterns)(transformPattern(_).map(MinusPattern))
        case OptionalPattern(patterns)        => ZIO.foreach(patterns)(transformPattern(_).map(OptionalPattern))
        case UnionPattern(blocks) =>
          val transformedBlocks: Seq[Task[Seq[QueryPattern]]] =
            blocks.flatMap(_.map(transformPattern))
          ZIO.collectAll(transformedBlocks).map(blocks => Seq(UnionPattern(blocks))) // TODO: check if this is correct
        case lucenePattern: LuceneQueryPattern => transformLuceneQueryPattern(lucenePattern)
        case pattern: QueryPattern             => ZIO.succeed(Seq(pattern))
      }

    for {
      optimisedPatterns <-
        ZIO.attempt(
          SparqlTransformer.moveBindToBeginning(
            SparqlTransformer.optimiseIsDeletedWithFilter(
              SparqlTransformer.moveLuceneToBeginning(inputQuery.whereClause.patterns)
            )
          )
        )
      transformedPatterns <- ZIO.foreach(optimisedPatterns)(transformPattern)
      patterns             = transformedPatterns.flatten
    } yield inputQuery.copy(whereClause = WhereClause(patterns))
  }

}
