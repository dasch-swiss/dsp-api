/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import akka.http.scaladsl.model.{HttpCharsets, MediaType}
import org.knora.webapi._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{SmartIri, StringFormatter}

/**
  * Constants used in processing SPARQL queries.
  */
object SparqlQueryConstants {
    /**
      * The media type of SPARQL queries.
      */
    val `application/sparql-query`: MediaType.WithFixedCharset = MediaType.customWithFixedCharset(
        mainType = "application",
        subType = "sparql-query",
        charset = HttpCharsets.`UTF-8`,
        fileExtensions = List("rq")
    )
}

/**
  * Represents something that can generate SPARQL source code.
  */
sealed trait SparqlGenerator {
    def toSparql: String
}

/**
  * Represents something that can be the subject, predicate, or object of a triple pattern in a query.
  */
sealed trait Entity extends Expression

/**
  * Represents something that can be returned by a SELECT query.
  */
trait SelectQueryColumn extends Entity

/**
  * Represents a variable in a query.
  *
  * @param variableName the name of the variable.
  */
case class QueryVariable(variableName: String) extends SelectQueryColumn {
    def toSparql: String = s"?$variableName"
}

/**
  * Represents a GROUP_CONCAT statement that combines several values into one, separated by a character.
  *
  * @param inputVariable      the variable to be concatenated.
  * @param separator          the separator to be used when concatenating the single results.
  * @param outputVariableName the name of the variable representing the concatenated result.
  */
case class GroupConcat(inputVariable: QueryVariable, separator: Char, outputVariableName: String) extends SelectQueryColumn {

    val outputVariable = QueryVariable(outputVariableName)

    def toSparql: String = {
        s"(GROUP_CONCAT(${inputVariable.toSparql}; SEPARATOR='$separator') AS ${outputVariable.toSparql})"
    }
}

/**
  * Represents a COUNT statement that counts how many instances/rows are returned for [[inputVariable]].
  *
  * @param inputVariable the variable to count.
  * @param distinct indicates whether DISTINCT has to be used inside COUNT.
  * @param outputVariableName the name of the variable representing the result.
  */
case class Count(inputVariable: QueryVariable, distinct: Boolean, outputVariableName: String) extends SelectQueryColumn {

    val outputVariable = QueryVariable(outputVariableName)

    val distinctAsStr: String = if (distinct) {
        "DISTINCT"
    } else {
        ""
    }

    def toSparql: String = {

        s"(COUNT($distinctAsStr ${inputVariable.toSparql}) AS ${outputVariable.toSparql})"
    }

}

/**
  * Represents an IRI in a query.
  *
  * @param iri the IRI.
  */
case class IriRef(iri: SmartIri, propertyPathOperator: Option[Char] = None) extends Entity {
    /**
      * If this is a knora-api entity IRI, converts it to an internal entity IRI.
      *
      * @return the equivalent internal entity IRI.
      */
    def toInternalEntityIri: IriRef = IriRef(iri.toOntologySchema(InternalSchema))

    def toSparql: String = {
        if (propertyPathOperator.nonEmpty) {
            s"<$iri>${propertyPathOperator.get}"
        } else {
            s"<$iri>"
        }
    }
}

/**
  * Represents a literal value with an XSD type.
  *
  * @param value    the literal value.
  * @param datatype the value's XSD type IRI.
  */
case class XsdLiteral(value: String, datatype: SmartIri) extends Entity {
    def toSparql: String = "\"" + value + "\"^^<" + datatype + ">"
}

/**
  * Represents a statement pattern or block pattern in a query.
  */
sealed trait QueryPattern extends SparqlGenerator

/**
  * Represents a statement pattern in a query.
  *
  * @param subj       the subject of the statement.
  * @param pred       the predicate of the statement.
  * @param obj        the object of the statement.
  * @param namedGraph the named graph this statement should be searched in. Defaults to [[None]].
  */
case class StatementPattern(subj: Entity, pred: Entity, obj: Entity, namedGraph: Option[IriRef] = None) extends QueryPattern {
    def toSparql: String = {
        val triple = s"${subj.toSparql} ${pred.toSparql} ${obj.toSparql} ."

        namedGraph match {
            case Some(graph) =>
                s"""GRAPH ${graph.toSparql} {
                   |    $triple
                   |}
                   |""".stripMargin

            case None =>
                triple + "\n"
        }
    }
}

/**
  * Represents a BIND command in a query.
  *
  * @param variable the variable in the BIND.
  * @param iriValue the value of the variable, which must be a Knora data IRI.
  */
case class BindPattern(variable: QueryVariable, iriValue: IriRef) extends QueryPattern {
    def toSparql: String = {
        s"BIND(${iriValue.toSparql} AS ${variable.toSparql})\n"
    }
}

/**
  * Provides convenience methods for making statement patterns that are marked as needing inference or not.
  */
object StatementPattern {
    /**
      * Makes a [[StatementPattern]] whose named graph is [[OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph]].
      *
      * @param subj the subject of the statement.
      * @param pred the predicate of the statement.
      * @param obj  the object of the statement.
      * @return the statement pattern.
      */
    def makeExplicit(subj: Entity, pred: Entity, obj: Entity): StatementPattern = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        StatementPattern(
            subj = subj,
            pred = pred,
            obj = obj,
            namedGraph = Some(IriRef(OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph.toSmartIri))
        )
    }

    /**
      * Makes a [[StatementPattern]] that doesn't specify a named graph.
      *
      * @param subj the subject of the statement.
      * @param pred the predicate of the statement.
      * @param obj  the object of the statement.
      * @return the statement pattern.
      */
    def makeInferred(subj: Entity, pred: Entity, obj: Entity): StatementPattern = {
        StatementPattern(
            subj = subj,
            pred = pred,
            obj = obj,
            namedGraph = None
        )
    }
}

/**
  * Represents the supported logical operators in a [[CompareExpression]].
  */
object CompareExpressionOperator extends Enumeration {

    val EQUALS: CompareExpressionOperator.Value = Value("=")

    val GREATER_THAN: CompareExpressionOperator.Value = Value(">")

    val GREATER_THAN_OR_EQUAL_TO: CompareExpressionOperator.Value = Value(">=")

    val LESS_THAN: CompareExpressionOperator.Value = Value("<")

    val LESS_THAN_OR_EQUAL_TO: CompareExpressionOperator.Value = Value("<=")

    val NOT_EQUALS: CompareExpressionOperator.Value = Value("!=")

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, the provided error function is called.
      *
      * @param name     the name of the value.
      * @param errorFun the function to be called in case of an error.
      * @return the requested value.
      */
    def lookup(name: String, errorFun: => Nothing): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => errorFun
        }
    }
}

/**
  * Represents an expression that can be used in a FILTER.
  */
sealed trait Expression extends SparqlGenerator

/**
  * Represents a comparison expression in a FILTER.
  *
  * @param leftArg  the left argument.
  * @param operator the operator.
  * @param rightArg the right argument.
  */
case class CompareExpression(leftArg: Expression, operator: CompareExpressionOperator.Value, rightArg: Expression) extends Expression {
    def toSparql: String = s"(${leftArg.toSparql} $operator ${rightArg.toSparql})"
}

/**
  * Represents an AND expression in a filter.
  *
  * @param leftArg  the left argument.
  * @param rightArg the right argument.
  */
case class AndExpression(leftArg: Expression, rightArg: Expression) extends Expression {
    def toSparql: String = s"(${leftArg.toSparql} && ${rightArg.toSparql})"
}

/**
  * Represents an OR expression in a filter.
  *
  * @param leftArg  the left argument.
  * @param rightArg the right argument.
  */
case class OrExpression(leftArg: Expression, rightArg: Expression) extends Expression {
    def toSparql: String = s"(${leftArg.toSparql} || ${rightArg.toSparql})"
}

/**
  * Represents a regex function in a query (in a FILTER).
  *
  * @param textValueVar the variable representing the text value to be checked against the provided pattern.
  * @param pattern the REGEX pattern to be used.
  * @param modifier the modifier to be used.
  */
case class RegexFunction(textValueVar: QueryVariable, pattern: String, modifier: String) extends Expression {
    def toSparql: String = s"""regex(${textValueVar.toSparql}, "$pattern", "$modifier")"""
}

/**
  * Represents a lang function in  a query (in a FILTER).
  *
  * @param textValueVar the variable representing the text value to be restricted to the specified language.
  */
case class LangFunction(textValueVar: QueryVariable) extends Expression {
    def toSparql: String = s"""lang(${textValueVar.toSparql})"""
}

/**
  * Represents a function call in a filter.
  *
  * @param functionIri the IRI of the function.
  * @param args the arguments passed to the function.
  */
case class FunctionCallExpression(functionIri: IriRef, args: Seq[Entity]) extends Expression {
    def toSparql: String = s"<${functionIri.iri.toString}>(${args.map(_.toSparql).mkString(", ")})"

    /**
      * Gets the argument at the given position as a [[QueryVariable]].
      * Throws a [[GravsearchException]] no argument exists at the given position or if it is not a [[QueryVariable]].
      *
      * @param pos the argument to be returned from [[args]].
      * @return a [[QueryVariable]].
      */
    def getArgAsQueryVar(pos: Int): QueryVariable = {

        if (args.size <= pos) throw GravsearchException(s"Not enough arguments given for call of $functionIri. ${args.size} are given, argument at position $pos is requested (0-based index)")

        args(pos) match {
            case queryVar: QueryVariable => queryVar

            case other => throw GravsearchException(s"$other is expected to be a QueryVariable")
        }

    }

    /**
      * Gets the argument at the given position as a [[XsdLiteral]] of the given datatype.
      * Throws a [[GravsearchException]] no argument exists at the given position or if it is not a [[XsdLiteral]] of the requested datatype.
      *
      * @param pos the argument to be returned from [[args]].
      * @param xsdDatatype the argeument's datatype.
      * @return an [[XsdLiteral]].
      */
    def getArgAsLiteral(pos: Int, xsdDatatype: SmartIri): XsdLiteral = {

        if (args.size <= pos) throw GravsearchException(s"Not enough arguments given for call of $functionIri. ${args.size} are given, argument at position $pos is requested (0-based index)")

        args(pos) match {
            case literal: XsdLiteral if literal.datatype == xsdDatatype => literal

            case other => throw GravsearchException(s"$other is expected to be a literal of type ${xsdDatatype.toString}")

        }

    }
}

/**
  * Represents a FILTER pattern in a query.
  *
  * @param expression the expression in the FILTER.
  */
case class FilterPattern(expression: Expression) extends QueryPattern {
    def toSparql: String = s"FILTER(${expression.toSparql})\n"
}

/**
  * Represents VALUES in a query.
  *
  * @param variable the variable that the values will be assigned to.
  * @param values   the IRIs that will be assigned to the variable.
  */
case class ValuesPattern(variable: QueryVariable, values: Set[IriRef]) extends QueryPattern {
    def toSparql: String = s"VALUES ${variable.toSparql} { ${values.map(_.toSparql).mkString(" ")} }\n"
}

/**
  * Represents a UNION in the WHERE clause of a query.
  *
  * @param blocks the blocks of patterns contained in the UNION.
  */
case class UnionPattern(blocks: Seq[Seq[QueryPattern]]) extends QueryPattern {
    def toSparql: String = {
        val blocksAsStrings = blocks.map {
            block: Seq[QueryPattern] =>
                val queryPatternStrings: Seq[String] = block.map {
                    queryPattern: QueryPattern => queryPattern.toSparql
                }

                queryPatternStrings.mkString
        }

        "{\n" + blocksAsStrings.mkString("} UNION {\n") + "}\n"
    }
}

/**
  * Represents an OPTIONAL in the WHERE clause of a query.
  *
  * @param patterns the patterns in the OPTIONAL block.
  */
case class OptionalPattern(patterns: Seq[QueryPattern]) extends QueryPattern {
    def toSparql: String = {
        val queryPatternStrings: Seq[String] = patterns.map {
            queryPattern: QueryPattern => queryPattern.toSparql
        }

        "OPTIONAL {\n" + queryPatternStrings.mkString + "}\n"
    }
}

/**
  * Represents a FILTER NOT EXISTS in a query.
  *
  * @param patterns the patterns contained in the FILTER NOT EXISTS.
  */
case class FilterNotExistsPattern(patterns: Seq[QueryPattern]) extends QueryPattern {
    def toSparql: String = s"FILTER NOT EXISTS {\n ${patterns.map(_.toSparql).mkString}\n}\n"
}

/**
  * Represents a MINUS in a query.
  *
  * @param patterns the patterns contained in the MINUS.
  */
case class MinusPattern(patterns: Seq[QueryPattern]) extends QueryPattern {
    def toSparql: String = s"MINUS {\n ${patterns.map(_.toSparql).mkString}\n}\n"
}


/**
  * Represents a CONSTRUCT clause in a query.
  *
  * @param statements the statements in the CONSTRUCT clause.
  */
case class ConstructClause(statements: Seq[StatementPattern]) extends SparqlGenerator {
    def toSparql: String = "CONSTRUCT {\n" + statements.map(_.toSparql).mkString + "} "
}

/**
  * Represents a WHERE clause in a query.
  *
  * @param patterns the patterns in the WHERE clause.
  */
case class WhereClause(patterns: Seq[QueryPattern], positiveEntities: Set[Entity] = Set.empty[Entity]) extends SparqlGenerator {
    def toSparql: String = "WHERE {\n" + patterns.map(_.toSparql).mkString + "}\n"
}

/**
  * Represents a criterion to order by.
  *
  * @param queryVariable the variable used for ordering.
  * @param isAscending   indicates if the order is ascending or descending.
  */
case class OrderCriterion(queryVariable: QueryVariable, isAscending: Boolean) extends SparqlGenerator {
    def toSparql: String = if (isAscending) {
        s"ASC(${queryVariable.toSparql})"
    } else {
        s"DESC(${queryVariable.toSparql})"
    }
}

/**
  * Represents a SPARQL CONSTRUCT query.
  *
  * @param constructClause the CONSTRUCT clause.
  * @param whereClause     the WHERE clause.
  * @param orderBy         the variables that the results should be ordered by.
  */
case class ConstructQuery(constructClause: ConstructClause, whereClause: WhereClause, orderBy: Seq[OrderCriterion] = Seq.empty[OrderCriterion], offset: Long = 0) extends SparqlGenerator {
    def toSparql: String = {
        val stringBuilder = new StringBuilder
        stringBuilder.append(constructClause.toSparql).append(whereClause.toSparql)

        if (offset > 0) {
            stringBuilder.append("OFFSET ").append(offset)
        }

        stringBuilder.toString
    }
}

/**
  * Represents a SPARQL SELECT query.
  *
  * @param variables   the variables to be returned by the query.
  * @param useDistinct indicates if DISTINCT should be used.
  * @param whereClause the WHERE clause.
  * @param orderBy     the variables that the results should be ordered by.
  * @param limit       the maximum number of result rows to be returned.
  * @param offset      the offset to be used (limit of the previous query + 1 to do paging).
  */
case class SelectQuery(variables: Seq[SelectQueryColumn], useDistinct: Boolean = true, whereClause: WhereClause, groupBy: Seq[QueryVariable] = Seq.empty[QueryVariable], orderBy: Seq[OrderCriterion] = Seq.empty[OrderCriterion], limit: Option[Int] = None, offset: Long = 0) extends SparqlGenerator {
    def toSparql: String = {
        val selectWhereSparql = "SELECT " + {
            if (useDistinct) {
                "DISTINCT "
            } else {
                ""
            }
        } + variables.map(_.toSparql).mkString(" ") + "\n" + whereClause.toSparql + "\n"

        val groupBySparql = if (groupBy.nonEmpty) {
            "GROUP BY " + groupBy.map(_.toSparql).mkString(" ") + "\n"
        } else {
            ""
        }

        val orderBySparql = if (orderBy.nonEmpty) {
            "ORDER BY " + orderBy.map(_.toSparql).mkString(" ")
        } else {
            ""
        }

        val offsetSparql = s"\nOFFSET $offset"

        val limitSparql = if (limit.nonEmpty) {
            s"\nLIMIT ${limit.get}"
        } else {
            ""
        }

        selectWhereSparql + groupBySparql + orderBySparql + offsetSparql + limitSparql


    }
}