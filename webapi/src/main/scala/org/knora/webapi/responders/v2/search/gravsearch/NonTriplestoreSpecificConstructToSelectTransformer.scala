package org.knora.webapi.responders.v2.search.sparql.gravsearch

import org.knora.webapi._
import org.knora.webapi.util.SmartIri
import org.knora.webapi.responders.v2.search.sparql._
import org.knora.webapi.responders.v2.search.sparql.gravsearch.GravsearchUtilV2.SparqlTransformation.createUniqueVariableNameFromEntityAndProperty
import org.knora.webapi.util.IriConversions._

/**
  * Transforms a preprocessed CONSTRUCT query into a SELECT query that returns only the IRIs and sort order of the main resources that matched
  * the search criteria. This query will be used to get resource IRIs for a single page of results. These IRIs will be included in a CONSTRUCT
  * query to get the actual results for the page.
  *
  * @param typeInspectionResult the result of type inspection of the original query.
  * @param querySchema ontology schema used in the input query.
  * @param settings application settings.
  */
class NonTriplestoreSpecificConstructToSelectTransformer(typeInspectionResult: GravsearchTypeInspectionResult, querySchema: ApiV2Schema, settings: SettingsImpl) extends AbstractSparqlTransformer(typeInspectionResult, querySchema) with ConstructToSelectTransformer {

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
        // Return the main resource variable and the generated variable that we're using for ordering.

        val dependentResourceGroupConcat: Set[GroupConcat] = dependentResourceVariables.map {
            dependentResVar: QueryVariable =>
                GroupConcat(inputVariable = dependentResVar,
                    separator = groupConcatSeparator,
                    outputVariableName = dependentResVar.variableName + groupConcatVariableSuffix)
        }.toSet

        dependentResourceVariablesGroupConcat = dependentResourceGroupConcat.map(_.outputVariable)

        val valueObjectGroupConcat = valueObjectVariables.map {
            valueObjVar: QueryVariable =>
                GroupConcat(inputVariable = valueObjVar,
                    separator = groupConcatSeparator,
                    outputVariableName = valueObjVar.variableName + groupConcatVariableSuffix)
        }.toSet

        valueObjectVarsGroupConcat = valueObjectGroupConcat.map(_.outputVariable)

        mainResourceVariable match {
            case Some(mainVar: QueryVariable) => Seq(mainVar) ++ dependentResourceGroupConcat ++ valueObjectGroupConcat

            case None => throw GravsearchException(s"No ${OntologyConstants.KnoraBase.IsMainResource.toSmartIri.toSparql} found in CONSTRUCT query.")
        }

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
                // Did a FILTER already generate a unique variable for the literal value of this value object?

                getGeneratedVariableForValueLiteralInOrderBy(criterion.queryVariable) match {
                    case Some(generatedVariable) =>
                        // Yes. Use the already generated variable in the ORDER BY.
                        acc.copy(
                            orderBy = acc.orderBy :+ OrderCriterion(queryVariable = generatedVariable, isAscending = criterion.isAscending)
                        )

                    case None =>
                        // No. Generate such a variable and generate an additional statement to get its literal value in the WHERE clause.

                        val propertyIri: SmartIri = typeInspectionResult.getTypeOfEntity(criterion.queryVariable) match {
                            case Some(nonPropertyTypeInfo: NonPropertyTypeInfo) =>
                                valueTypesToValuePredsForOrderBy.getOrElse(nonPropertyTypeInfo.typeIri.toString, throw GravsearchException(s"${criterion.queryVariable.toSparql} cannot be used in ORDER BY")).toSmartIri

                            case Some(_) => throw GravsearchException(s"Variable ${criterion.queryVariable.toSparql} represents a property, and therefore cannot be used in ORDER BY")

                            case None => throw GravsearchException(s"No type information found for ${criterion.queryVariable.toSparql}")
                        }

                        // Generate the variable name.
                        val variableForLiteral: QueryVariable = createUniqueVariableNameFromEntityAndProperty(criterion.queryVariable, propertyIri.toString)

                        // Generate a statement to get the literal value.
                        val statementPattern = StatementPattern.makeExplicit(subj = criterion.queryVariable, pred = IriRef(propertyIri), obj = variableForLiteral)

                        acc.copy(
                            statementPatterns = acc.statementPatterns :+ statementPattern,
                            orderBy = acc.orderBy :+ OrderCriterion(queryVariable = variableForLiteral, isAscending = criterion.isAscending)
                        )
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

