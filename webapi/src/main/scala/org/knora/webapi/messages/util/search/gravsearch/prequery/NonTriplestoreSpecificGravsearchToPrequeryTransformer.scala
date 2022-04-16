/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import org.knora.webapi._
import org.knora.webapi.exceptions.{AssertionException, GravsearchException}
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.util.search.gravsearch.types.{
  GravsearchTypeInspectionResult,
  GravsearchTypeInspectionUtil,
  NonPropertyTypeInfo,
  PropertyTypeInfo
}
import org.knora.webapi.settings.KnoraSettingsImpl
import scala.concurrent.ExecutionContext
import org.knora.webapi.messages.SmartIri

/**
 * Transforms a preprocessed CONSTRUCT query into a SELECT query that returns only the IRIs and sort order of the main resources that matched
 * the search criteria and are requested by client in the input query's WHERE clause. This query will be used to get resource IRIs for a single
 * page of results. These IRIs will be included in a CONSTRUCT query to get the actual results for the page.
 *
 * @param constructClause      the CONSTRUCT clause from the input query.
 * @param typeInspectionResult the result of type inspection of the input query.
 * @param querySchema          the ontology schema used in the input query.
 * @param settings             application settings.
 * @param featureFactoryConfig the feature factory configuration.
 */
class NonTriplestoreSpecificGravsearchToPrequeryTransformer(
  constructClause: ConstructClause,
  typeInspectionResult: GravsearchTypeInspectionResult,
  querySchema: ApiV2Schema,
  settings: KnoraSettingsImpl,
  featureFactoryConfig: FeatureFactoryConfig
) extends AbstractPrequeryGenerator(
      constructClause = constructClause,
      typeInspectionResult = typeInspectionResult,
      querySchema = querySchema
    )
    with ConstructToSelectTransformer {

  import AbstractPrequeryGenerator._

  /**
   * Transforms a [[org.knora.webapi.messages.util.search.StatementPattern]] in a WHERE clause into zero or more query patterns.
   *
   * @param statementPattern the statement to be transformed.
   * @param inputOrderBy     the ORDER BY clause in the input query.
   * @return the result of the transformation.
   */
  override def transformStatementInWhere(
    statementPattern: StatementPattern,
    inputOrderBy: Seq[OrderCriterion],
    limitInferenceToOntologies: Option[Set[SmartIri]] = None // TODO-BL: should I have to pass this here?
  )(implicit executionContext: ExecutionContext): Seq[QueryPattern] =
    // Include any statements needed to meet the user's search criteria, but not statements that would be needed for permission checking or
    // other information about the matching resources or values.
    processStatementPatternFromWhereClause(
      statementPattern = statementPattern,
      inputOrderBy = inputOrderBy
    )

  /**
   * Transforms a [[org.knora.webapi.messages.util.search.FilterPattern]] in a WHERE clause into zero or more statement patterns.
   *
   * @param filterPattern the filter to be transformed.
   * @return the result of the transformation.
   */
  override def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = {

    val filterExpression: TransformedFilterPattern =
      transformFilterPattern(filterPattern.expression, typeInspectionResult = typeInspectionResult, isTopLevel = true)

    filterExpression.expression match {
      case Some(expression: Expression) => filterExpression.additionalPatterns :+ FilterPattern(expression)

      case None => filterExpression.additionalPatterns // no FILTER expression given
    }

  }

  /**
   * Determines whether an entity has a property type that meets the specified condition.
   *
   * @param entity    the entity.
   * @param condition the condition.
   * @return `true` if the variable has a property type and the condition is met.
   */
  private def entityHasPropertyType(entity: Entity, condition: PropertyTypeInfo => Boolean): Boolean =
    GravsearchTypeInspectionUtil.maybeTypeableEntity(entity) match {
      case Some(typeableEntity) =>
        typeInspectionResult.entities.get(typeableEntity) match {
          case Some(propertyTypeInfo: PropertyTypeInfo) => condition(propertyTypeInfo)
          case Some(_: NonPropertyTypeInfo)             => false
          case None                                     => false
        }

      case None => false
    }

  /**
   * Determines whether an entity has a non-property type that meets the specified condition.
   *
   * @param entity    the entity.
   * @param condition the condition.
   * @return `true` if the variable has a non-property type and the condition is met.
   */
  private def entityHasNonPropertyType(entity: Entity, condition: NonPropertyTypeInfo => Boolean): Boolean =
    GravsearchTypeInspectionUtil.maybeTypeableEntity(entity) match {
      case Some(typeableEntity) =>
        typeInspectionResult.entities.get(typeableEntity) match {
          case Some(nonPropertyTypeInfo: NonPropertyTypeInfo) => condition(nonPropertyTypeInfo)
          case Some(_: PropertyTypeInfo)                      => false
          case None                                           => false
        }

      case None => false
    }

  /**
   * Checks that an [[Entity]] is a [[QueryVariable]].
   *
   * @param entity the entity.
   * @return the entity as a [[QueryVariable]].
   */
  private def entityToQueryVariable(entity: Entity): QueryVariable =
    entity match {
      case queryVariable: QueryVariable => queryVariable
      case other                        => throw GravsearchException(s"Expected a variable in CONSTRUCT clause, but found ${other.toSparql}")
    }

  /**
   * All the variables used in the Gravsearch CONSTRUCT clause.
   */
  private val variablesInConstruct: Set[QueryVariable] = constructClause.statements.flatMap {
    statementPattern: StatementPattern =>
      Seq(statementPattern.subj, statementPattern.obj).flatMap {
        case queryVariable: QueryVariable => Some(queryVariable)
        case _                            => None
      }
  }.toSet

  /**
   * The variables representing values in the CONSTRUCT clause, grouped by resource.
   */
  private val valueVariablesPerResourceInConstruct: Map[Entity, Set[QueryVariable]] =
    constructClause.statements.filter { statementPattern: StatementPattern =>
      // Find statements in which the subject is a resource, the predicate is a value property,
      // and the object is a value.
      entityHasNonPropertyType(entity = statementPattern.subj, condition = _.isResourceType) &&
      entityHasPropertyType(entity = statementPattern.pred, condition = _.objectIsValueType) &&
      entityHasNonPropertyType(entity = statementPattern.obj, condition = _.isValueType)
    }.map { statementPattern =>
      statementPattern.subj -> entityToQueryVariable(statementPattern.obj)
    }.groupBy { case (resourceEntity, _) =>
      resourceEntity
    }.map { case (resourceEntity, resourceValueTuples) =>
      // Simplify the result of groupBy by replacing each tuple with its second element.
      resourceEntity -> resourceValueTuples.map(_._2).toSet
    }

  /**
   * The variables representing resources in the CONSTRUCT clause.
   */
  private val resourceVariablesInConstruct: Set[QueryVariable] = variablesInConstruct.filter { queryVariable =>
    entityHasNonPropertyType(entity = queryVariable, condition = _.isResourceType)
  }

  // If a variable is used as the subject or object of a statement pattern in the CONSTRUCT clause, and it
  // doesn't represent a resource or a value, that's an error.

  private val valueVariablesInConstruct: Set[QueryVariable] = valueVariablesPerResourceInConstruct.values.flatten.toSet

  private val invalidVariablesInConstruct: Set[QueryVariable] =
    variablesInConstruct -- valueVariablesInConstruct -- resourceVariablesInConstruct

  if (invalidVariablesInConstruct.nonEmpty) {
    val invalidVariablesWithTypes: Set[String] = invalidVariablesInConstruct.map { queryVariable =>
      val typeableEntity = GravsearchTypeInspectionUtil.toTypeableEntity(queryVariable)

      val typeName = typeInspectionResult.entities
        .get(typeableEntity)
        .map {
          case _: PropertyTypeInfo                      => "property"
          case nonPropertyTypeInfo: NonPropertyTypeInfo => s"<${nonPropertyTypeInfo.typeIri}>"
        }
        .getOrElse("unknown type")

      s"${queryVariable.toSparql} ($typeName)"
    }

    throw GravsearchException(
      s"One or more variables in the Gravsearch CONSTRUCT clause have unknown or invalid types: ${invalidVariablesWithTypes
        .mkString(", ")}"
    )
  }

  /**
   * The [[GroupConcat]] expressions generated for values in the prequery, grouped by resource entity.
   */
  private val valueGroupConcatsPerResource: Map[Entity, Set[GroupConcat]] = {
    // Generate variables representing link values and group them by containing resource entity.
    val linkValueVariablesPerResourceGeneratedForConstruct: Map[Entity, Set[QueryVariable]] =
      constructClause.statements.filter { statementPattern: StatementPattern =>
        // Find statements in which the subject is a resource, the predicate is a link property,
        // and the object is a resource.
        entityHasNonPropertyType(entity = statementPattern.subj, condition = _.isResourceType) &&
        entityHasPropertyType(entity = statementPattern.pred, condition = _.objectIsResourceType) &&
        entityHasNonPropertyType(entity = statementPattern.obj, condition = _.isResourceType)
      }.map { statementPattern =>
        // For each of those statements, make a variable representing a link value.
        statementPattern.subj -> SparqlTransformer.createUniqueVariableFromStatementForLinkValue(
          baseStatement = statementPattern
        )
      }.groupBy { case (resourceEntity, _) =>
        resourceEntity
      }.map { case (resourceEntity, resourceValueTuples) =>
        // Simplify the result of groupBy by replacing each tuple with its second element.
        resourceEntity -> resourceValueTuples.map(_._2).toSet
      }

    // Make a GroupConcat for each value variable.
    (valueVariablesPerResourceInConstruct.keySet ++ linkValueVariablesPerResourceGeneratedForConstruct.keySet).map {
      resourceEntity: Entity =>
        val valueVariables: Set[QueryVariable] =
          valueVariablesPerResourceInConstruct.getOrElse(resourceEntity, Set.empty) ++
            linkValueVariablesPerResourceGeneratedForConstruct.getOrElse(resourceEntity, Set.empty)

        val groupConcats: Set[GroupConcat] = valueVariables.map { valueObjVar: QueryVariable =>
          GroupConcat(
            inputVariable = valueObjVar,
            separator = groupConcatSeparator,
            outputVariableName = valueObjVar.variableName + groupConcatVariableSuffix
          )
        }

        resourceEntity -> groupConcats
    }
  }.toMap

  /**
   * The variables used in [[GroupConcat]] expressions in the prequery, grouped by resource entity.
   */
  private val valueGroupConcatVariablesPerResource: Map[Entity, Set[QueryVariable]] = {
    valueGroupConcatsPerResource.map { case (resourceEntity: Entity, groupConcats: Set[GroupConcat]) =>
      resourceEntity -> groupConcats.map(_.outputVariable)
    }
  }

  /**
   * A GROUP_CONCAT expression for each value variable.
   */
  private val valueObjectGroupConcat: Set[GroupConcat] = valueGroupConcatsPerResource.values.flatten.toSet

  /**
   * Variables representing dependent resources in the CONSTRUCT clause.
   */
  private val dependentResourceVariablesInConstruct: Set[QueryVariable] =
    resourceVariablesInConstruct - mainResourceVariable

  /**
   * A GROUP_CONCAT expression for each dependent resource variable.
   */
  private val dependentResourceGroupConcat: Set[GroupConcat] = dependentResourceVariablesInConstruct.map {
    dependentResVar: QueryVariable =>
      GroupConcat(
        inputVariable = dependentResVar,
        separator = groupConcatSeparator,
        outputVariableName = dependentResVar.variableName + groupConcatVariableSuffix
      )
  }

  /**
   * The variable names used in the GROUP_CONCAT expressions for dependent resources.
   */
  // TODO only used by GravsearchMainQueryGenerator and not clear why
  val dependentResourceVariablesGroupConcat: Set[QueryVariable] = dependentResourceGroupConcat.map(_.outputVariable)

  /**
   * The variable names used in the GROUP_CONCAT expressions for values.
   */
  // TODO same as above
  val valueObjectVariablesGroupConcat: Set[QueryVariable] = valueGroupConcatVariablesPerResource.values.flatten.toSet

  /**
   * Returns the columns to be specified in the SELECT query.
   */
  override def getSelectColumns: Seq[SelectQueryColumn] =
    Seq(mainResourceVariable) ++ dependentResourceGroupConcat ++ valueObjectGroupConcat

  /**
   * Returns the variables that were used in [[GroupConcat]] expressions in the prequery to represent values
   * that were mentioned in the CONSTRUCT clause of the input query, for the given entity representing a resource.
   */
  def getValueGroupConcatVariablesForResource(resourceEntity: Entity): Set[QueryVariable] =
    valueGroupConcatVariablesPerResource.getOrElse(resourceEntity, Set.empty)

  /**
   * Returns the criteria, if any, that should be used in the ORDER BY clause of the SELECT query. This method will be called
   * by [[org.knora.webapi.messages.util.search.QueryTraverser]] after the whole input query has been traversed.
   *
   * @return the ORDER BY criteria, if any.
   */
  override def getOrderBy(inputOrderBy: Seq[OrderCriterion]): TransformedOrderBy = {

    val transformedOrderBy = inputOrderBy.foldLeft(TransformedOrderBy()) { case (acc, criterion) =>
      // A unique variable for the literal value of this value object should already have been created

      getGeneratedVariableForValueLiteralInOrderBy(criterion.queryVariable) match {
        case Some(generatedVariable) =>
          // Yes. Use the already generated variable in the ORDER BY.
          acc.copy(
            orderBy =
              acc.orderBy :+ OrderCriterion(queryVariable = generatedVariable, isAscending = criterion.isAscending)
          )

        case None =>
          // No.
          throw GravsearchException(
            s"Variable ${criterion.queryVariable.toSparql} is used in ORDER by, but is not bound at the top level of the WHERE clause"
          )

      }
    }

    // main resource variable as order by criterion
    val orderByMainResVar: OrderCriterion = OrderCriterion(
      queryVariable = mainResourceVariable,
      isAscending = true
    )

    // order by: user provided variables and main resource variable
    // all variables present in the GROUP BY must be included in the order by statements to make the results predictable for paging
    transformedOrderBy.copy(
      orderBy = transformedOrderBy.orderBy :+ orderByMainResVar
    )
  }

  /**
   * Creates the GROUP BY statement based on the ORDER BY statement.
   *
   * @param orderByCriteria the criteria used to sort the query results. They have to be included in the GROUP BY statement, otherwise they are unbound.
   * @return a list of variables that the result rows are grouped by.
   */
  def getGroupBy(orderByCriteria: TransformedOrderBy): Seq[QueryVariable] =
    // get they query variables form the order by criteria and return them in reverse order:
    // main resource variable first, followed by other sorting criteria, if any.
    orderByCriteria.orderBy.map(_.queryVariable).reverse

  /**
   * Gets the maximal amount of result rows to be returned by the prequery.
   *
   * @return the LIMIT, if any.
   */
  def getLimit: Int =
    // get LIMIT from settings
    settings.v2ResultsPerPage

  /**
   * Gets the OFFSET to be used in the prequery (needed for paging).
   *
   * @param inputQueryOffset the OFFSET provided in the input query.
   * @param limit            the maximum amount of result rows to be returned by the prequery.
   * @return the OFFSET.
   */
  def getOffset(inputQueryOffset: Long, limit: Int): Long = {

    if (inputQueryOffset < 0) throw AssertionException("Negative OFFSET is illegal.")

    // determine offset for paging -> multiply given offset with limit (indicating the maximum amount of results per page).
    inputQueryOffset * limit

  }

  override def transformLuceneQueryPattern(luceneQueryPattern: LuceneQueryPattern): Seq[QueryPattern] =
    Seq(luceneQueryPattern)

  /**
   * Runs optimisations that take a Gravsearch query as input. An optimisation needs to be run here if
   * it uses the type inspection result that refers to the Gravsearch query.
   *
   * @param patterns the query patterns to be optimised.
   * @return the optimised query patterns.
   */
  override def optimiseQueryPatterns(patterns: Seq[QueryPattern]): Seq[QueryPattern] =
    GravsearchQueryOptimisationFactory
      .getGravsearchQueryOptimisationFeature(
        typeInspectionResult = typeInspectionResult,
        querySchema = querySchema,
        featureFactoryConfig = featureFactoryConfig
      )
      .optimiseQueryPatterns(patterns)
}
