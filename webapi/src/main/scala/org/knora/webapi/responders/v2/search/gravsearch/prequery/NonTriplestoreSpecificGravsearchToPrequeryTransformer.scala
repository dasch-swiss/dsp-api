/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2.search.gravsearch.prequery

import org.knora.webapi._
import org.knora.webapi.responders.v2.search._
import org.knora.webapi.responders.v2.search.gravsearch.types._
import org.knora.webapi.util.IriConversions._

/**
 * Transforms a preprocessed CONSTRUCT query into a SELECT query that returns only the IRIs and sort order of the main resources that matched
 * the search criteria. This query will be used to get resource IRIs for a single page of results. These IRIs will be included in a CONSTRUCT
 * query to get the actual results for the page.
 *
 * @param constructClause      the CONSTRUCT clause from the input query.
 * @param typeInspectionResult the result of type inspection of the original query.
 * @param querySchema          ontology schema used in the input query.
 * @param settings             application settings.
 */
class NonTriplestoreSpecificGravsearchToPrequeryTransformer(constructClause: ConstructClause, typeInspectionResult: GravsearchTypeInspectionResult, querySchema: ApiV2Schema, settings: SettingsImpl)
    extends AbstractPrequeryGenerator(typeInspectionResult, querySchema) with ConstructToSelectTransformer {

    /**
     * Collects information from a statement pattern in the CONSTRUCT clause of the input query, e.g. variables
     * that need to be returned by the SELECT.
     *
     * @param statementPattern the statement to be handled.
     */
    override def handleStatementInConstruct(statementPattern: StatementPattern): Unit = {
        // Just identify the main resource variable and put it in mainResourceVariable.

        isMainResourceVariable(statementPattern) match {
            case Some(queryVariable: QueryVariable) => mainResourceVariable = Some(queryVariable)
            case None => ()
        }

    }

    /**
     * Transforms a [[StatementPattern]] in a WHERE clause into zero or more query patterns.
     *
     * @param statementPattern the statement to be transformed.
     * @param inputOrderBy     the ORDER BY clause in the input query.
     * @return the result of the transformation.
     */
    override def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[QueryPattern] = {
        // Include any statements needed to meet the user's search criteria, but not statements that would be needed for permission checking or
        // other information about the matching resources or values.

        processStatementPatternFromWhereClause(
            statementPattern = statementPattern,
            inputOrderBy = inputOrderBy
        )
    }

    /**
     * Transforms a [[FilterPattern]] in a WHERE clause into zero or more statement patterns.
     *
     * @param filterPattern the filter to be transformed.
     * @return the result of the transformation.
     */
    override def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = {

        val filterExpression: TransformedFilterPattern = transformFilterPattern(filterPattern.expression, typeInspectionResult = typeInspectionResult, isTopLevel = true)

        filterExpression.expression match {
            case Some(expression: Expression) => filterExpression.additionalPatterns :+ FilterPattern(expression)

            case None => filterExpression.additionalPatterns // no FILTER expression given
        }

    }

    /**
     * Returns the variables that should be included in the results of the SELECT query. This method will be called
     * by [[QueryTraverser]] after the whole input query has been traversed.
     *
     * @return the variables that should be returned by the SELECT.
     */
    override def getSelectVariables: Seq[SelectQueryColumn] = {
        /**
         * Determines whether an entity has a non-property type that meets the specified condition.
         *
         * @param entity    the entity.
         * @param condition the condition.
         * @return `true` if the variable has a non-property type and the condition is met.
         */
        def entityHasNonPropertyType(entity: Entity, condition: NonPropertyTypeInfo => Boolean): Boolean = {
            GravsearchTypeInspectionUtil.maybeTypeableEntity(entity) match {
                case Some(typeableEntity) =>
                    typeInspectionResult.entities.get(typeableEntity) match {
                        case Some(nonPropertyTypeInfo: NonPropertyTypeInfo) => condition(nonPropertyTypeInfo)
                        case Some(_: PropertyTypeInfo) => false
                        case None => false
                    }

                case None => false
            }
        }

        // Collect the query variables used in the Gravsearch CONSTRUCT clause.
        val variablesInConstruct = constructClause.statements.flatMap {
            statementPattern: StatementPattern =>
                Seq(statementPattern.subj, statementPattern.obj).flatMap {
                    case queryVariable: QueryVariable => Some(queryVariable)
                    case _ => None
                }
        }.toSet

        // Identify the variables representing resources.
        val resourceVariablesInConstruct: Set[QueryVariable] = variablesInConstruct.filter {
            queryVariable => entityHasNonPropertyType(entity = queryVariable, condition = _.isResourceType)
        }

        // Identify the variables representing values.
        val valueVariablesInConstruct: Set[QueryVariable] = variablesInConstruct.filter {
            queryVariable => entityHasNonPropertyType(entity = queryVariable, condition = _.isValueType)
        }

        // If a variable is used as the subject or object of a statement pattern in the CONSTRUCT clause, and it
        // doesn't represent a resource or a value, that's an error.
        val invalidVariablesInConstruct = variablesInConstruct -- valueVariablesInConstruct -- resourceVariablesInConstruct

        if (invalidVariablesInConstruct.nonEmpty) {
            val invalidVariablesWithTypes: Set[String] = invalidVariablesInConstruct.map {
                queryVariable =>
                    val typeableEntity = GravsearchTypeInspectionUtil.toTypeableEntity(queryVariable)

                    val typeName = typeInspectionResult.entities.get(typeableEntity).map {
                        case _: PropertyTypeInfo => "property"
                        case nonPropertyTypeInfo: NonPropertyTypeInfo => s"<${nonPropertyTypeInfo.typeIri}>"
                    }.getOrElse("unknown type")

                    s"${queryVariable.toSparql} ($typeName)"
            }

            throw GravsearchException(s"One or more variables in the Gravsearch CONSTRUCT clause have unknown or invalid types: ${invalidVariablesWithTypes.mkString(", ")}")
        }

        // Generate variables representing link values.
        val linkValueVariables: Set[QueryVariable] = constructClause.statements.filter {
            statementPattern: StatementPattern =>
                entityHasNonPropertyType(entity = statementPattern.subj, condition = _.isResourceType) &&
                    entityHasNonPropertyType(entity = statementPattern.obj, condition = _.isResourceType)
        }.map {
            statementPattern =>
                SparqlTransformer.createUniqueVariableFromStatementForLinkValue(
                    baseStatement = StatementPattern(
                        subj = statementPattern.subj,
                        pred = statementPattern.pred,
                        obj = statementPattern.obj
                    )
                )
        }.toSet

        // Make sure the CONSTRUCT clause mentions the main resource variable.
        val mainResVar: QueryVariable = mainResourceVariable match {
            case Some(mainVar: QueryVariable) =>
                if (resourceVariablesInConstruct.contains(mainVar)) {
                    mainVar
                } else {
                    throw GravsearchException(s"The Gravsearch CONSTRUCT clause does not refer to the main resource variable")
                }

            case None =>
                throw GravsearchException(s"The Gravsearch query does not specify a main resource variable")
        }

        // Make a set of dependent resource variables.
        val dependentResourceVariablesInConstruct = resourceVariablesInConstruct - mainResVar

        // Generate a GROUP_CONCAT expression for each dependent resource variable.
        val dependentResourceGroupConcat: Set[GroupConcat] = dependentResourceVariablesInConstruct.map {
            dependentResVar: QueryVariable =>
                GroupConcat(inputVariable = dependentResVar,
                    separator = groupConcatSeparator,
                    outputVariableName = dependentResVar.variableName + groupConcatVariableSuffix)
        }

        // Store the variable names used in those GROUP_CONCAT expressions.
        dependentResourceVariablesGroupConcat = dependentResourceGroupConcat.map(_.outputVariable)

        // Generate a GROUP_CONCAT expression for each value variable.
        val valueObjectGroupConcat = (valueVariablesInConstruct ++ linkValueVariables).map {
            valueObjVar: QueryVariable =>
                GroupConcat(inputVariable = valueObjVar,
                    separator = groupConcatSeparator,
                    outputVariableName = valueObjVar.variableName + groupConcatVariableSuffix)
        }

        // Store the variable names used in those GROUP_CONCAT expressions.
        valueObjectVarsGroupConcat = valueObjectGroupConcat.map(_.outputVariable)

        // Return columns for the main resource variable and for the GROUP_CONCAT expressions.
        Seq(mainResVar) ++ dependentResourceGroupConcat ++ valueObjectGroupConcat
    }

    /**
     * Returns the criteria, if any, that should be used in the ORDER BY clause of the SELECT query. This method will be called
     * by [[QueryTraverser]] after the whole input query has been traversed.
     *
     * @return the ORDER BY criteria, if any.
     */
    override def getOrderBy(inputOrderBy: Seq[OrderCriterion]): TransformedOrderBy = {

        val transformedOrderBy = inputOrderBy.foldLeft(TransformedOrderBy()) {
            case (acc, criterion) =>
                // A unique variable for the literal value of this value object should already have been created

                getGeneratedVariableForValueLiteralInOrderBy(criterion.queryVariable) match {
                    case Some(generatedVariable) =>
                        // Yes. Use the already generated variable in the ORDER BY.
                        acc.copy(
                            orderBy = acc.orderBy :+ OrderCriterion(queryVariable = generatedVariable, isAscending = criterion.isAscending)
                        )

                    case None =>
                        // No.
                        throw GravsearchException(s"Not value literal variable was automatically generated for ${criterion.queryVariable}")

                }
        }

        // main resource variable as order by criterion
        val orderByMainResVar: OrderCriterion = OrderCriterion(
            queryVariable = mainResourceVariable.getOrElse(throw GravsearchException(s"No ${OntologyConstants.KnoraBase.IsMainResource.toSmartIri.toSparql} found in CONSTRUCT query")),
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
    def getGroupBy(orderByCriteria: TransformedOrderBy): Seq[QueryVariable] = {
        // get they query variables form the order by criteria and return them in reverse order:
        // main resource variable first, followed by other sorting criteria, if any.
        orderByCriteria.orderBy.map(_.queryVariable).reverse
    }

    /**
     * Gets the maximal amount of result rows to be returned by the prequery.
     *
     * @return the LIMIT, if any.
     */
    def getLimit: Int = {
        // get LIMIT from settings
        settings.v2ResultsPerPage
    }

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
}
