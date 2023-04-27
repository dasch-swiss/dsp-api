/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search

import zio._

import dsp.errors.GravsearchOptimizationException
import org.knora.webapi.InternalSchema
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.gravsearch.prequery.AbstractPrequeryGenerator
import org.knora.webapi.messages.util.search.gravsearch.transformers.SelectTransformer
import org.knora.webapi.messages.util.search.gravsearch.transformers.WhereTransformer
import org.knora.webapi.slice.ontology.repo.service.OntologyCache

/**
 * A trait for classes that visit statements and filters in WHERE clauses, accumulating some result.
 *
 * @tparam Acc the type of the accumulator.
 */
trait WhereVisitor[Acc] {

  /**
   * Visits a statement in a WHERE clause.
   *
   * @param statementPattern the pattern to be visited.
   * @param acc              the accumulator.
   * @return the accumulator.
   */
  def visitStatementInWhere(statementPattern: StatementPattern, acc: Acc): Acc

  /**
   * Visits a FILTER in a WHERE clause.
   *
   * @param filterPattern the pattern to be visited.
   * @param acc           the accumulator.
   * @return the accumulator.
   */
  def visitFilter(filterPattern: FilterPattern, acc: Acc): Acc
}

/**
 * Returned by `ConstructToSelectTransformer.getOrderBy` to represent a transformed ORDER BY as well
 * as any additional statement patterns that should be added to the WHERE clause to support the ORDER BY.
 *
 * @param statementPatterns any additional WHERE clause statements required by the ORDER BY.
 * @param orderBy           the ORDER BY criteria.
 */
case class TransformedOrderBy(
  statementPatterns: Seq[StatementPattern] = Vector.empty[StatementPattern],
  orderBy: Seq[OrderCriterion] = Vector.empty[OrderCriterion]
)

/**
 * Assists in the transformation of CONSTRUCT queries by traversing the query, delegating work to a [[ConstructToConstructTransformer]]
 * or [[AbstractPrequeryGenerator]].
 */
final case class QueryTraverser(
  private val messageRelay: MessageRelay,
  private val ontologyCache: OntologyCache,
  implicit private val stringFormatter: StringFormatter
) {

  /**
   * Traverses a WHERE clause, delegating transformation tasks to a [[WhereTransformer]], and returns the transformed query patterns.
   *
   * @param patterns                   the input query patterns.
   * @param inputOrderBy               the ORDER BY expression in the input query.
   * @param whereTransformer           a [[WhereTransformer]].
   * @param limitInferenceToOntologies a set of ontology IRIs, to which the simulated inference will be limited. If `None`, all possible inference will be done.
   * @return the transformed query patterns.
   */
  private def transformWherePatterns(
    patterns: Seq[QueryPattern],
    inputOrderBy: Seq[OrderCriterion],
    whereTransformer: WhereTransformer,
    limitInferenceToOntologies: Set[SmartIri]
  ): Task[Seq[QueryPattern]] =
    for {
      // Optimization has to be called before WhereTransformer.transformStatementInWhere,
      // because optimisation might remove statements that would otherwise be expanded by transformStatementInWhere.
      optimisedPatterns <- whereTransformer.optimiseQueryPatterns(patterns)
      transformedPatterns <- ZIO.foreach(optimisedPatterns) {
                               case statementPattern: StatementPattern =>
                                 whereTransformer.transformStatementInWhere(
                                   statementPattern = statementPattern,
                                   inputOrderBy = inputOrderBy,
                                   limitInferenceToOntologies = limitInferenceToOntologies
                                 )

                               case filterPattern: FilterPattern =>
                                 whereTransformer.transformFilter(filterPattern)

                               case filterNotExistsPattern: FilterNotExistsPattern =>
                                 transformWherePatterns(
                                   patterns = filterNotExistsPattern.patterns,
                                   whereTransformer = whereTransformer,
                                   inputOrderBy = inputOrderBy,
                                   limitInferenceToOntologies = limitInferenceToOntologies
                                 ).map(patterns => Seq(FilterNotExistsPattern(patterns)))

                               case minusPattern: MinusPattern =>
                                 transformWherePatterns(
                                   patterns = minusPattern.patterns,
                                   whereTransformer = whereTransformer,
                                   inputOrderBy = inputOrderBy,
                                   limitInferenceToOntologies = limitInferenceToOntologies
                                 ).map(patterns => Seq(MinusPattern(patterns)))

                               case optionalPattern: OptionalPattern =>
                                 transformWherePatterns(
                                   patterns = optionalPattern.patterns,
                                   whereTransformer = whereTransformer,
                                   inputOrderBy = inputOrderBy,
                                   limitInferenceToOntologies = limitInferenceToOntologies
                                 ).map(patterns => Seq(OptionalPattern(patterns)))

                               case unionPattern: UnionPattern =>
                                 val transformedBlocks: Seq[Task[Seq[QueryPattern]]] = unionPattern.blocks.map {
                                   blockPatterns: Seq[QueryPattern] =>
                                     whereTransformer
                                       .enteringUnionBlock()
                                       .zipRight(
                                         transformWherePatterns(
                                           patterns = blockPatterns,
                                           whereTransformer = whereTransformer,
                                           inputOrderBy = inputOrderBy,
                                           limitInferenceToOntologies = limitInferenceToOntologies
                                         )
                                       )
                                       .zipLeft(
                                         whereTransformer.leavingUnionBlock()
                                       )
                                 }
                                 ZIO.collectAll(transformedBlocks).map(blocks => Seq(UnionPattern(blocks)))

                               case luceneQueryPattern: LuceneQueryPattern =>
                                 whereTransformer.transformLuceneQueryPattern(luceneQueryPattern)

                               case valuesPattern: ValuesPattern => ZIO.succeed(Seq(valuesPattern))

                               case bindPattern: BindPattern => ZIO.succeed(Seq(bindPattern))
                             }
    } yield transformedPatterns.flatten

  /**
   * Traverses a WHERE clause, delegating transformation tasks to a [[WhereVisitor]].
   *
   * @param patterns     the input query patterns.
   * @param whereVisitor a [[WhereVisitor]].
   * @param initialAcc   the visitor's initial accumulator.
   * @tparam Acc the type of the accumulator.
   * @return the accumulator.
   */
  def visitWherePatterns[Acc](patterns: Seq[QueryPattern], whereVisitor: WhereVisitor[Acc], initialAcc: Acc): Acc =
    patterns.foldLeft(initialAcc) {
      case (acc, statementPattern: StatementPattern) =>
        whereVisitor.visitStatementInWhere(statementPattern, acc)

      case (acc, filterPattern: FilterPattern) =>
        whereVisitor.visitFilter(filterPattern, acc)

      case (acc, filterNotExistsPattern: FilterNotExistsPattern) =>
        visitWherePatterns(
          patterns = filterNotExistsPattern.patterns,
          whereVisitor = whereVisitor,
          initialAcc = acc
        )

      case (acc, minusPattern: MinusPattern) =>
        visitWherePatterns(
          patterns = minusPattern.patterns,
          whereVisitor = whereVisitor,
          initialAcc = acc
        )

      case (acc, optionalPattern: OptionalPattern) =>
        visitWherePatterns(
          patterns = optionalPattern.patterns,
          whereVisitor = whereVisitor,
          initialAcc = acc
        )

      case (acc, unionPattern: UnionPattern) =>
        unionPattern.blocks.foldLeft(acc) { case (unionAcc, blockPatterns: Seq[QueryPattern]) =>
          visitWherePatterns(
            patterns = blockPatterns,
            whereVisitor = whereVisitor,
            initialAcc = unionAcc
          )
        }

      case (acc, _) => acc
    }

  /**
   * Extracts the [[StatementPattern]] containing the type definition of the `knora-base:isMainResource`
   * from a construct query.
   * Prefers more specific types over `knora-base:Resource`, if available.
   * If multiple types are present, the first one is returned.
   *
   * @param query the input query.
   * @return the [[StatementPattern]] containing the type definition, if one is found; None otherwise.
   */
  private def findMainResourceType(query: ConstructQuery): Option[StatementPattern] =
    for {
      mainResourceName <-
        query.constructClause.statements.collectFirst {
          case StatementPattern(QueryVariable(name), IriRef(pred, _), _)
              if pred
                .toOntologySchema(InternalSchema)
                .toString == OntologyConstants.KnoraBase.IsMainResource =>
            name
        }
      mainResourceTypeStatement <-
        query.whereClause.patterns.collectFirst {
          case stmt @ StatementPattern(QueryVariable(mainResourceName), IriRef(pred, _), obj: IriRef)
              if pred.toIri == OntologyConstants.Rdf.Type &&
                obj.iri.toOntologySchema(InternalSchema).toIri != OntologyConstants.KnoraBase.Resource =>
            stmt.copy(obj = obj.copy(iri = obj.iri.toOntologySchema(InternalSchema)))
        }.orElse(query.whereClause.patterns.collectFirst {
          case stmt @ StatementPattern(QueryVariable(mainResourceName), IriRef(pred, _), obj: IriRef)
              if pred.toIri == OntologyConstants.Rdf.Type =>
            stmt.copy(obj = obj.copy(iri = obj.iri.toOntologySchema(InternalSchema)))
        })
    } yield mainResourceTypeStatement

  /**
   * Ensures that the query contains at least one pattern that is not a negation pattern (MINUS or FILTER NOT EXISTS).
   * It does so by adding a type statement, if that is the case.
   *
   * @param patterns the optimized patterns, potentially containing only negations.
   * @param inputQuery the initial input query.
   * @return succeeds with a sequence of [[QueryPatterns]] or fails with a [[GravsearchOptimizationException]].
   */
  private def ensureNotOnlyNegationPatterns(
    patterns: Seq[QueryPattern],
    inputQuery: ConstructQuery
  ): Task[Seq[QueryPattern]] =
    patterns match {
      case MinusPattern(_) +: Nil =>
        ZIO
          .fromOption(findMainResourceType(inputQuery))
          .map(patterns.appended(_))
          .orElseFail(
            GravsearchOptimizationException(
              s"Query consisted only of a MINUS pattern after optimization, which always returns empty results. Query: ${inputQuery.toSparql}"
            )
          )
      case FilterNotExistsPattern(_) +: Nil =>
        ZIO
          .fromOption(findMainResourceType(inputQuery))
          .map(patterns.appended(_))
          .orElseFail(
            GravsearchOptimizationException(
              s"Query consisted only of a FILTER NOT EXISTS pattern after optimization, which always returns empty results. Query: ${inputQuery.toSparql}"
            )
          )
      case _ => ZIO.succeed(patterns)
    }

  /**
   * Traverses a SELECT query, delegating transformation tasks to a [[ConstructToSelectTransformer]], and returns the transformed query.
   *
   * @param inputQuery                 the query to be transformed.
   * @param transformer                the [[ConstructToSelectTransformer]] to be used.
   * @param limitInferenceToOntologies a set of ontology IRIs, to which the simulated inference will be limited. If `None`, all possible inference will be done.
   * @return the transformed query.
   */
  def transformConstructToSelect(
    inputQuery: ConstructQuery,
    transformer: AbstractPrequeryGenerator,
    limitInferenceToOntologies: Set[SmartIri]
  ): Task[SelectQuery] = for {
    transformedWherePatterns <- transformWherePatterns(
                                  patterns = inputQuery.whereClause.patterns,
                                  inputOrderBy = inputQuery.orderBy,
                                  whereTransformer = transformer,
                                  limitInferenceToOntologies = limitInferenceToOntologies
                                )
    transformedOrderBy              <- transformer.getOrderBy(inputQuery.orderBy)
    patterns                         = transformedWherePatterns ++ transformedOrderBy.statementPatterns
    patternsEnsuringNotOnlyNegation <- ensureNotOnlyNegationPatterns(patterns, inputQuery)
    groupBy                         <- transformer.getGroupBy(transformedOrderBy)
    limit                           <- transformer.getLimit
    offset                          <- transformer.getOffset(inputQuery.offset, limit)
    variables                       <- transformer.getSelectColumns
  } yield SelectQuery(
    variables = variables,
    whereClause = WhereClause(patterns = patternsEnsuringNotOnlyNegation),
    groupBy = groupBy,
    orderBy = transformedOrderBy.orderBy,
    limit = Some(limit),
    offset = offset
  )

  def transformSelectToSelect(
    inputQuery: SelectQuery,
    transformer: SelectToSelectTransformer,
    limitInferenceToOntologies: Set[SmartIri]
  ): Task[SelectQuery] =
    for {
      fromClause <- transformer.getFromClause
      patterns <- transformWherePatterns(
                    patterns = inputQuery.whereClause.patterns,
                    inputOrderBy = inputQuery.orderBy,
                    whereTransformer = transformer,
                    limitInferenceToOntologies = limitInferenceToOntologies
                  )
      whereClause = WhereClause(patterns)
    } yield inputQuery.copy(fromClause = fromClause, whereClause = whereClause)
}

object QueryTraverser {
  val layer: URLayer[MessageRelay with OntologyCache with StringFormatter, QueryTraverser] =
    ZLayer.fromFunction(QueryTraverser.apply _)
}
