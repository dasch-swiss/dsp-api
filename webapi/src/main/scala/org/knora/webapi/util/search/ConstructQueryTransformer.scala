/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.util.search

/**
  * A trait for classes that transform statements and filters in CONSTRUCT queries. Such a class will probably need
  * to refer to a [[TypeInspectionResult]].
  */
trait QueryPatternTransformer {

    /**
      * Transforms a [[StatementPattern]] in a CONSTRUCT clause into zero or more statement patterns.
      *
      * @param statementPattern the statement to be transformed.
      * @return the result of the transformation.
      */
    def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern]

    /**
      * Transforms a [[StatementPattern]] in a WHERE clause into zero or more query patterns.
      *
      * @param statementPattern the statement to be transformed.
      * @return the result of the transformation.
      */
    def transformStatementInWhere(statementPattern: StatementPattern): Seq[QueryPattern]

    /**
      * Transforms a [[FilterPattern]] in a WHERE clause into zero or more statement patterns.
      *
      * @param filterPattern the filter to be transformed.
      * @return the result of the transformation.
      */
    def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern]
}

/**
  * Assists in the transformation of CONSTRUCT queries by traversing the query, delegating work to a [[QueryPatternTransformer]].
  */
object ConstructQueryTransformer {

    private def transformWherePatterns(patterns: Seq[QueryPattern], queryPatternTransformer: QueryPatternTransformer): Seq[QueryPattern] = {
        patterns.flatMap {
            case statementPattern: StatementPattern =>
                queryPatternTransformer.transformStatementInWhere(
                    statementPattern = statementPattern
                )

            case filterPattern: FilterPattern =>
                queryPatternTransformer.transformFilter(
                    filterPattern = filterPattern
                )

            case optionalPattern: OptionalPattern =>
                val transformedPatterns = transformWherePatterns(
                    patterns = optionalPattern.patterns,
                    queryPatternTransformer = queryPatternTransformer
                )

                Seq(OptionalPattern(patterns = transformedPatterns))

            case unionPattern: UnionPattern =>
                val transformedBlocks = unionPattern.blocks.map {
                    blockPatterns =>
                        transformWherePatterns(patterns = blockPatterns,
                            queryPatternTransformer = queryPatternTransformer
                        )
                }

                Seq(UnionPattern(blocks = transformedBlocks))
        }
    }

    /**
      * Traverses a CONSTRUCT query, delegating transformation tasks to a [[QueryPatternTransformer]], and returns the transformed query.
      *
      * @param inputQuery              the query to be transformed.
      * @param queryPatternTransformer the [[QueryPatternTransformer]] to be used.
      * @return the transformed query.
      */
    def transformQuery(inputQuery: ConstructQuery, queryPatternTransformer: QueryPatternTransformer): ConstructQuery = {

        // TODO: the current design would not be able to check permissions for properties and resources that are contained in the Where clause but not in the Construct clause
        // TODO: This could lead to a situation in which the user looks for a resource relating to another resource or a property which he has no permissions to see, but still he would find that very resource relating to it.
        // TODO: Should we enforce that statements in the Where clause have to be repeated in the Construct clause?
        // TODO: Or should we include Where patterns in the Construct clause for permission checking and filter them out later?

        val transformedConstructStatements: Seq[StatementPattern] = inputQuery.constructClause.statements.flatMap {
            statementPattern => queryPatternTransformer.transformStatementInConstruct(statementPattern)
        }

        val transformedWherePatterns = transformWherePatterns(
            patterns = inputQuery.whereClause.patterns,
            queryPatternTransformer = queryPatternTransformer
        )

        ConstructQuery(
            constructClause = ConstructClause(statements = transformedConstructStatements),
            whereClause = WhereClause(patterns = transformedWherePatterns)
        )
    }
}
