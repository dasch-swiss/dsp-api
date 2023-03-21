/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search

import zio._

import dsp.errors.BadRequestException
import org.knora.webapi.InternalSchema
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
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
 * A trait for classes that transform statements and filters in WHERE clauses.
 */
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
    limitInferenceToOntologies: Option[Set[SmartIri]] = None
  ): Task[Seq[QueryPattern]]

  /**
   * Transforms a [[FilterPattern]] in a WHERE clause into zero or more query patterns.
   *
   * @param filterPattern the filter to be transformed.
   * @return the result of the transformation.
   */
  def transformFilter(filterPattern: FilterPattern): Task[Seq[QueryPattern]]

  /**
   * Transforms a [[LuceneQueryPattern]] into one or more query patterns.
   *
   * @param luceneQueryPattern the query pattern to be transformed.
   * @return the transformed pattern.
   */
  def transformLuceneQueryPattern(luceneQueryPattern: LuceneQueryPattern): Task[Seq[QueryPattern]]
}

/**
 * A trait for classes that transform SELECT queries into other SELECT queries.
 */
trait SelectToSelectTransformer extends WhereTransformer {

  /**
   * Transforms a [[StatementPattern]] in a SELECT's WHERE clause into zero or more statement patterns.
   *
   * @param statementPattern the statement to be transformed.
   * @return the result of the transformation.
   */
  def transformStatementInSelect(statementPattern: StatementPattern): Task[Seq[StatementPattern]]

  /**
   * Specifies a FROM clause, if needed.
   *
   * @return the FROM clause to be used, if any.
   */
  def getFromClause: Task[Option[FromClause]]
}

/**
 * A trait for classes that transform CONSTRUCT queries into other CONSTRUCT queries.
 */
trait ConstructToConstructTransformer extends WhereTransformer {

  /**
   * Transforms a [[StatementPattern]] in a CONSTRUCT clause into zero or more statement patterns.
   *
   * @param statementPattern the statement to be transformed.
   * @return the result of the transformation.
   */
  def transformStatementInConstruct(statementPattern: StatementPattern): Task[Seq[StatementPattern]]
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
 * A trait for classes that transform SELECT queries into CONSTRUCT queries.
 */
trait ConstructToSelectTransformer extends WhereTransformer {

  /**
   * Returns the columns to be specified in the SELECT query.
   */
  def getSelectColumns: Task[Seq[SelectQueryColumn]]

  /**
   * Returns the variables that the query result rows are grouped by (aggregating rows into one).
   * Variables returned by the SELECT query must either be present in the GROUP BY statement
   * or be transformed by an aggregation function in SPARQL.
   * This method will be called by [[QueryTraverser]] after the whole input query has been traversed.
   *
   * @param orderByCriteria the criteria used to sort the query results. They have to be included in the GROUP BY statement, otherwise they are unbound.
   * @return a list of variables that the result rows are grouped by.
   */
  def getGroupBy(orderByCriteria: TransformedOrderBy): Task[Seq[QueryVariable]]

  /**
   * Returns the criteria, if any, that should be used in the ORDER BY clause of the SELECT query. This method will be called
   * by [[QueryTraverser]] after the whole input query has been traversed.
   *
   * @param inputOrderBy the ORDER BY criteria in the input query.
   * @return the ORDER BY criteria, if any.
   */
  def getOrderBy(inputOrderBy: Seq[OrderCriterion]): Task[TransformedOrderBy]

  /**
   * Returns the limit representing the maximum amount of result rows returned by the SELECT query.
   *
   * @return the LIMIT, if any.
   */
  def getLimit: Task[Int]

  /**
   * Returns the OFFSET to be used in the SELECT query.
   * Provided the OFFSET submitted in the input query, calculates the actual offset in result rows depending on LIMIT.
   *
   * @param inputQueryOffset the OFFSET provided in the input query.
   * @return the OFFSET.
   */
  def getOffset(inputQueryOffset: Long, limit: Int): Task[Long]
}

/**
 * Assists in the transformation of CONSTRUCT queries by traversing the query, delegating work to a [[ConstructToConstructTransformer]]
 * or [[ConstructToSelectTransformer]].
 */
final case class QueryTraverser(
  messageRelay: MessageRelay,
  ontologyCache: OntologyCache,
  implicit val stringFormatter: StringFormatter
) {

  /**
   * Helper method that analyzed an RDF Entity and returns a sequence of Ontology IRIs that are being referenced by the entity.
   * If an IRI appears that can not be resolved by the ontology cache, it will check if the IRI points to project data;
   * if so, all ontologies defined by the project to which the data belongs, will be included in the results.
   *
   * @param entity       an RDF entity.
   * @param map          a map of entity IRIs to the IRIs of the ontology where they are defined.
   * @return a sequence of ontology IRIs which relate to the input RDF entity.
   */
  private def resolveEntity(entity: Entity, map: Map[SmartIri, SmartIri]): Task[Seq[SmartIri]] =
    entity match {
      case IriRef(iri, _) =>
        val internal     = iri.toOntologySchema(InternalSchema)
        val maybeOntoIri = map.get(internal)
        maybeOntoIri match {
          // if the map contains an ontology IRI corresponding to the entity IRI, then this can be returned
          case Some(iri) => ZIO.succeed(Seq(iri))
          case None      =>
            // if the map doesn't contain a corresponding ontology IRI, then the entity IRI points to a resource or value
            // in that case, all ontologies of the project, to which the entity belongs, should be returned.
            val shortcode = internal.getProjectCode
            shortcode match {
              case None        => ZIO.succeed(Seq.empty)
              case Some(value) =>
                // find the project with the shortcode

                for {
                  projectMaybe <-
                    messageRelay
                      .ask[Option[ProjectADM]](
                        ProjectGetADM(
                          ShortcodeIdentifier
                            .fromString(value)
                            .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
                        )
                      )
                  projectOntologies = projectMaybe match {
                                        case None => Seq.empty
                                        // return all ontologies of the project
                                        case Some(project) => project.ontologies.map(_.toSmartIri)
                                      }
                } yield projectOntologies
            }
        }
      case _ => ZIO.succeed(Seq.empty)
    }

  /**
   * Extracts all ontologies that are relevant to a gravsearch query, in order to allow optimized cache-based inference simulation.
   *
   * @param whereClause  the WHERE-clause of a gravsearch query.
   * @return a set of ontology IRIs relevant to the query, or `None`, if no meaningful result could be produced.
   *         In the latter case, inference should be done on the basis of all available ontologies.
   */
  def getOntologiesRelevantForInference(
    whereClause: WhereClause
  ): Task[Option[Set[SmartIri]]] = {
    // internal function for easy recursion
    // gets a sequence of [[QueryPattern]] and returns the set of entities that the patterns consist of
    def getEntities(patterns: Seq[QueryPattern]): Seq[Entity] =
      patterns.flatMap { pattern =>
        pattern match {
          case ValuesPattern(_, values)             => values.toSeq
          case BindPattern(_, expression)           => List(expression.asInstanceOf[Entity])
          case UnionPattern(blocks)                 => blocks.flatMap(block => getEntities(block))
          case StatementPattern(subj, pred, obj, _) => List(subj, pred, obj)
          case LuceneQueryPattern(subj, obj, _, _)  => List(subj, obj)
          case FilterNotExistsPattern(patterns)     => getEntities(patterns)
          case MinusPattern(patterns)               => getEntities(patterns)
          case OptionalPattern(patterns)            => getEntities(patterns)
          case _                                    => List.empty
        }
      }

    // get the entities for all patterns in the WHERE clause
    val entities = getEntities(whereClause.patterns)

    for {
      ontoCache <- ontologyCache.getCacheData
      // from the cache, get the map from entity to the ontology where the entity is defined
      entityMap = ontoCache.entityDefinedInOntology
      // resolve all entities from the WHERE clause to the ontology where they are defined
      relevantOntologies   <- ZIO.foreach(entities)(resolveEntity(_, entityMap))
      relevantOntologiesSet = relevantOntologies.flatten.toSet
      relevantOntologiesMaybe =
        relevantOntologiesSet match {
          case ontologies =>
            // if only knora-base was found, then None should be returned too
            if (ontologies == Set(OntologyConstants.KnoraBase.KnoraBaseOntologyIri.toSmartIri))
              None
            // in all other cases, it should be made sure that knora-base is contained in the result
            else Some(ontologies + OntologyConstants.KnoraBase.KnoraBaseOntologyIri.toSmartIri)
        }
    } yield relevantOntologiesMaybe
  }

  /**
   * Traverses a WHERE clause, delegating transformation tasks to a [[WhereTransformer]], and returns the transformed query patterns.
   *
   * @param patterns                   the input query patterns.
   * @param inputOrderBy               the ORDER BY expression in the input query.
   * @param whereTransformer           a [[WhereTransformer]].
   * @param limitInferenceToOntologies a set of ontology IRIs, to which the simulated inference will be limited. If `None`, all possible inference will be done.
   * @return the transformed query patterns.
   */
  def transformWherePatterns(
    patterns: Seq[QueryPattern],
    inputOrderBy: Seq[OrderCriterion],
    whereTransformer: WhereTransformer,
    limitInferenceToOntologies: Option[Set[SmartIri]] = None
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
   * Traverses a SELECT query, delegating transformation tasks to a [[ConstructToSelectTransformer]], and returns the transformed query.
   *
   * @param inputQuery                 the query to be transformed.
   * @param transformer                the [[ConstructToSelectTransformer]] to be used.
   * @param limitInferenceToOntologies a set of ontology IRIs, to which the simulated inference will be limited. If `None`, all possible inference will be done.
   * @return the transformed query.
   */
  def transformConstructToSelect(
    inputQuery: ConstructQuery,
    transformer: ConstructToSelectTransformer,
    limitInferenceToOntologies: Option[Set[SmartIri]] = None
  ): Task[SelectQuery] = for {
    transformedWherePatterns <- transformWherePatterns(
                                  patterns = inputQuery.whereClause.patterns,
                                  inputOrderBy = inputQuery.orderBy,
                                  whereTransformer = transformer,
                                  limitInferenceToOntologies = limitInferenceToOntologies
                                )
    transformedOrderBy <- transformer.getOrderBy(inputQuery.orderBy)
    groupBy            <- transformer.getGroupBy(transformedOrderBy)
    limit              <- transformer.getLimit
    offset             <- transformer.getOffset(inputQuery.offset, limit)
    variables          <- transformer.getSelectColumns
  } yield SelectQuery(
    variables = variables,
    whereClause = WhereClause(patterns = transformedWherePatterns ++ transformedOrderBy.statementPatterns),
    groupBy = groupBy,
    orderBy = transformedOrderBy.orderBy,
    limit = Some(limit),
    offset = offset
  )

  def transformSelectToSelect(
    inputQuery: SelectQuery,
    transformer: SelectToSelectTransformer,
    limitInferenceToOntologies: Option[Set[SmartIri]]
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

  /**
   * Traverses a CONSTRUCT query, delegating transformation tasks to a [[ConstructToConstructTransformer]], and returns the transformed query.
   *
   * @param inputQuery                 the query to be transformed.
   * @param transformer                the [[ConstructToConstructTransformer]] to be used.
   * @param limitInferenceToOntologies a set of ontology IRIs, to which the simulated inference will be limited. If `None`, all possible inference will be done.
   * @return the transformed query.
   */
  def transformConstructToConstruct(
    inputQuery: ConstructQuery,
    transformer: ConstructToConstructTransformer,
    limitInferenceToOntologies: Option[Set[SmartIri]] = None
  ): Task[ConstructQuery] =
    for {
      wherePatterns <- transformWherePatterns(
                         patterns = inputQuery.whereClause.patterns,
                         inputOrderBy = inputQuery.orderBy,
                         whereTransformer = transformer,
                         limitInferenceToOntologies = limitInferenceToOntologies
                       )
      constructStatements <-
        ZIO.foreach(inputQuery.constructClause.statements)(transformer.transformStatementInConstruct).map(_.flatten)
    } yield ConstructQuery(
      constructClause = ConstructClause(constructStatements),
      whereClause = WhereClause(wherePatterns)
    )
}

object QueryTraverser {
  val layer: URLayer[MessageRelay with OntologyCache with StringFormatter, QueryTraverser] =
    ZLayer.fromFunction(QueryTraverser.apply _)
}
