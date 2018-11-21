package org.knora.webapi.util.search.gravsearch

import org.knora.webapi.{ApiV2Schema, GravsearchException, OntologyConstants}
import org.knora.webapi.util.search._
import org.knora.webapi.util.IriConversions._

/**
  * Transforms a preprocessed CONSTRUCT query into a SELECT query that returns only the IRIs and sort order of the main resources that matched
  * the search criteria. This query will be used to get resource IRIs for a single page of results. These IRIs will be included in a CONSTRUCT
  * query to get the actual results for the page.
  *
  * @param typeInspectionResult the result of type inspection of the original query.
  */
class NonTriplestoreSpecificConstructToSelectTransformerCountQuery(typeInspectionResult: GravsearchTypeInspectionResult, querySchema: ApiV2Schema) extends AbstractSparqlTransformer(typeInspectionResult, querySchema) with ConstructToSelectTransformer {

    def handleStatementInConstruct(statementPattern: StatementPattern): Unit = {
        // Just identify the main resource variable and put it in mainResourceVariable.

        isMainResourceVariable(statementPattern) match {
            case Some(queryVariable: QueryVariable) => mainResourceVariable = Some(queryVariable)
            case None => ()
        }
    }

    def transformStatementInWhere(statementPattern: StatementPattern, inputOrderBy: Seq[OrderCriterion]): Seq[QueryPattern] = {

        // Include any statements needed to meet the user's search criteria, but not statements that would be needed for permission checking or
        // other information about the matching resources or values.

        processStatementPatternFromWhereClause(
            statementPattern = statementPattern,
            inputOrderBy = inputOrderBy
        )

    }

    def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = {
        val filterExpression: TransformedFilterPattern = transformFilterPattern(filterPattern.expression, typeInspectionResult = typeInspectionResult, isTopLevel = true)

        filterExpression.expression match {
            case Some(expression: Expression) => filterExpression.additionalPatterns :+ FilterPattern(expression)

            case None => filterExpression.additionalPatterns // no FILTER expression given
        }

    }

    def getSelectVariables: Seq[SelectQueryColumn] = {

        val mainResVar = mainResourceVariable match {
            case Some(mainVar: QueryVariable) => mainVar

            case None => throw GravsearchException(s"No ${OntologyConstants.KnoraBase.IsMainResource.toSmartIri.toSparql} found in CONSTRUCT query.")
        }

        // return count aggregation function for main variable
        Seq(Count(inputVariable = mainResVar, distinct = true, outputVariableName = "count"))
    }

    def getGroupBy(orderByCriteria: TransformedOrderBy): Seq[QueryVariable] = {
        Seq.empty[QueryVariable]
    }

    def getOrderBy(inputOrderBy: Seq[OrderCriterion]): TransformedOrderBy = {
        // empty by default
        TransformedOrderBy()
    }

    def getLimit: Int = 1 // one row expected for count query

    def getOffset(inputQueryOffset: Long, limit: Int): Long = {
        // count queries do not consider offsets since there is only one result row
        0
    }
}
