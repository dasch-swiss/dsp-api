/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import zio.*

import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

final case class ConstructTransformer(
  sparqlTransformerLive: OntologyInferencer,
  iriConverter: IriConverter
) {

  /**
   * Transforms a CONSTRUCT query, by applying opimization and inference.
   *
   * @param inputQuery                 the query to be transformed.
   * @param limitInferenceToOntologies a set of ontology IRIs, to which the simulated inference will be limited. If `None`, all possible inference will be done.
   * @return the transformed query.
   */
  def transform(
    inputQuery: ConstructQuery,
    limitInferenceToOntologies: Option[Set[SmartIri]] = None
  ): Task[ConstructQuery] =
    for {
      patterns <- optimizeAndTransformPatterns(inputQuery.whereClause.patterns, limitInferenceToOntologies)
    } yield inputQuery.copy(whereClause = WhereClause(patterns))

  private def optimizeAndTransformPatterns(
    patterns: Seq[QueryPattern],
    limit: Option[Set[SmartIri]]
  ): Task[Seq[QueryPattern]] = for {
    optimisedPatterns <-
      ZIO.attempt(
        SparqlTransformer.moveBindToBeginning(
          SparqlTransformer.optimiseIsDeletedWithFilter(
            SparqlTransformer.moveLuceneToBeginning(patterns)
          )
        )
      )
    transformedPatterns <- ZIO.foreach(optimisedPatterns)(transformPattern(_, limit))
  } yield transformedPatterns.flatten

  private def transformPattern(
    pattern: QueryPattern,
    limit: Option[Set[SmartIri]]
  ): Task[Seq[QueryPattern]] =
    pattern match {
      case statementPattern: StatementPattern =>
        sparqlTransformerLive.transformStatementInWhere(
          statementPattern = statementPattern,
          simulateInference = true,
          limitInferenceToOntologies = limit
        )
      case FilterNotExistsPattern(patterns) => transformInner(patterns, limit, FilterNotExistsPattern.apply)
      case MinusPattern(patterns)           => transformInner(patterns, limit, MinusPattern.apply)
      case OptionalPattern(patterns)        => transformInner(patterns, limit, OptionalPattern.apply)
      case UnionPattern(blocks) =>
        ZIO.foreach(blocks)(optimizeAndTransformPatterns(_, limit)).map(block => Seq(UnionPattern(block)))
      case pattern: QueryPattern => ZIO.succeed(Seq(pattern))
    }

  private def transformInner[Outer <: QueryPattern](
    inner: Seq[QueryPattern],
    limit: Option[Set[SmartIri]],
    outer: Seq[QueryPattern] => Outer
  ): Task[Seq[Outer]] =
    ZIO.foreach(inner)(transformPattern(_, limit)).map(t => Seq(outer.apply(t.flatten)))

}

object ConstructTransformer {
  val layer: URLayer[OntologyInferencer & IriConverter, ConstructTransformer] =
    ZLayer.fromFunction(ConstructTransformer.apply _)
}
