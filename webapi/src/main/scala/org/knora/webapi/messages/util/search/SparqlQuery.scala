/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search

import akka.http.scaladsl.model.HttpCharsets
import akka.http.scaladsl.model.MediaType

import dsp.errors.AssertionException
import dsp.errors.GravsearchException
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

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
  override def toSparql: String = s"?$variableName"

  override def getVariables: Set[QueryVariable] = Set(this)
}

/**
 * Represents a GROUP_CONCAT statement that combines several values into one, separated by a character.
 *
 * @param inputVariable      the variable to be concatenated.
 * @param separator          the separator to be used when concatenating the single results.
 * @param outputVariableName the name of the variable representing the concatenated result.
 */
case class GroupConcat(inputVariable: QueryVariable, separator: Char, outputVariableName: String)
    extends SelectQueryColumn {

  val outputVariable: QueryVariable = QueryVariable(outputVariableName)

  override def toSparql: String =
    s"""(GROUP_CONCAT(DISTINCT(IF(BOUND(${inputVariable.toSparql}), STR(${inputVariable.toSparql}), "")); SEPARATOR='$separator') AS ${outputVariable.toSparql})"""

  override def getVariables: Set[QueryVariable] = Set(inputVariable)
}

/**
 * Represents a COUNT statement that counts how many instances/rows are returned for [[inputVariable]].
 *
 * @param inputVariable      the variable to count.
 * @param distinct           indicates whether DISTINCT has to be used inside COUNT.
 * @param outputVariableName the name of the variable representing the result.
 */
case class Count(inputVariable: QueryVariable, distinct: Boolean, outputVariableName: String)
    extends SelectQueryColumn {

  val outputVariable: QueryVariable = QueryVariable(outputVariableName)

  val distinctAsStr: String = if (distinct) {
    "DISTINCT"
  } else {
    ""
  }

  override def toSparql: String =
    s"(COUNT($distinctAsStr ${inputVariable.toSparql}) AS ${outputVariable.toSparql})"

  override def getVariables: Set[QueryVariable] = Set(inputVariable, outputVariable)
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

  override def toSparql: String =
    if (propertyPathOperator.nonEmpty) {
      s"${iri.toSparql}${propertyPathOperator.get}"
    } else {
      iri.toSparql
    }

  def toOntologySchema(targetSchema: OntologySchema): IriRef =
    copy(iri = iri.toOntologySchema(targetSchema))

  override def getVariables: Set[QueryVariable] = Set.empty
}

/**
 * Represents a literal value with an XSD type.
 *
 * @param value    the literal value.
 * @param datatype the value's XSD type IRI.
 */
case class XsdLiteral(value: String, datatype: SmartIri) extends Entity {
  implicit private val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  override def toSparql: String = {
    // We use xsd:dateTimeStamp in Gravsearch, but xsd:dateTime in the triplestore.
    val transformedDatatype = if (datatype.toString == OntologyConstants.Xsd.DateTimeStamp) {
      OntologyConstants.Xsd.DateTime.toSmartIri
    } else {
      datatype
    }

    "\"" + value + "\"^^" + transformedDatatype.toSparql
  }

  override def getVariables: Set[QueryVariable] = Set.empty
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
case class StatementPattern(subj: Entity, pred: Entity, obj: Entity, namedGraph: Option[IriRef] = None)
    extends QueryPattern {
  override def toSparql: String = {
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

  def toOntologySchema(targetSchema: OntologySchema): StatementPattern =
    copy(
      subj = entityToOntologySchema(subj, targetSchema),
      pred = entityToOntologySchema(pred, targetSchema),
      obj = entityToOntologySchema(obj, targetSchema)
    )

  private def entityToOntologySchema(entity: Entity, targetSchema: OntologySchema): Entity =
    entity match {
      case iriRef: IriRef => iriRef.toOntologySchema(targetSchema)
      case other          => other
    }
}

/**
 * A virtual query pattern representing a Lucene full-text index search. Will be replaced by triplestore-specific
 * statements during Gravsearch processing.
 *
 * @param subj        a variable representing the subject to be found.
 * @param obj         a variable representing the literal that is indexed.
 * @param queryString the Lucene query string to be matched.
 * @param literalStatement a statement that connects `subj` to `obj`. Needed with some triplestores but not others.
 *                         Will be defined only if it has not already been added to the generated SPARQL.
 */
case class LuceneQueryPattern(
  subj: QueryVariable,
  obj: QueryVariable,
  queryString: LuceneQueryString,
  literalStatement: Option[StatementPattern]
) extends QueryPattern {
  override def toSparql: String =
    throw AssertionException("LuceneQueryPattern should have been transformed into statements")
}

/**
 * Represents a BIND command in a query.
 *
 * @param variable   the variable in the BIND.
 * @param expression the expression to be bound to the variable.
 */
case class BindPattern(variable: QueryVariable, expression: Expression) extends QueryPattern {
  override def toSparql: String =
    s"BIND(${expression.toSparql} AS ${variable.toSparql})\n"
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
  def makeInferred(subj: Entity, pred: Entity, obj: Entity): StatementPattern =
    StatementPattern(
      subj = subj,
      pred = pred,
      obj = obj,
      namedGraph = None
    )
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
  def lookup(name: String, errorFun: => Nothing): Value =
    valueMap.get(name) match {
      case Some(value) => value
      case None        => errorFun
    }
}

/**
 * Represents an expression that can be used in a FILTER.
 */
sealed trait Expression extends SparqlGenerator {

  /**
   * Returns the set of query variables used in this expression.
   */
  def getVariables: Set[QueryVariable]
}

/**
 * Represents a comparison expression in a FILTER.
 *
 * @param leftArg  the left argument.
 * @param operator the operator.
 * @param rightArg the right argument.
 */
case class CompareExpression(leftArg: Expression, operator: CompareExpressionOperator.Value, rightArg: Expression)
    extends Expression {
  override def toSparql: String = s"(${leftArg.toSparql} $operator ${rightArg.toSparql})"

  override def getVariables: Set[QueryVariable] = leftArg.getVariables ++ rightArg.getVariables
}

/**
 * Represents an AND expression in a filter.
 *
 * @param leftArg  the left argument.
 * @param rightArg the right argument.
 */
case class AndExpression(leftArg: Expression, rightArg: Expression) extends Expression {
  override def toSparql: String = s"(${leftArg.toSparql} && ${rightArg.toSparql})"

  override def getVariables: Set[QueryVariable] = leftArg.getVariables ++ rightArg.getVariables
}

/**
 * Represents an OR expression in a filter.
 *
 * @param leftArg  the left argument.
 * @param rightArg the right argument.
 */
case class OrExpression(leftArg: Expression, rightArg: Expression) extends Expression {
  override def toSparql: String = s"(${leftArg.toSparql} || ${rightArg.toSparql})"

  override def getVariables: Set[QueryVariable] = leftArg.getVariables ++ rightArg.getVariables
}

/**
 * A trait representing arithmetic operators.
 */
sealed trait ArithmeticOperator

/**
 * Represents the plus operator.
 */
case object PlusOperator extends ArithmeticOperator {
  override def toString = "+"
}

/**
 * Represents the minus operator.
 */
case object MinusOperator extends ArithmeticOperator {
  override def toString = "-"
}

/**
 * Represents an integer literal.
 */
case class IntegerLiteral(value: Int) extends Expression {
  override def toSparql: String = value.toString

  override def getVariables: Set[QueryVariable] = Set.empty
}

/**
 * Represents an arithmetic expression.
 */
case class ArithmeticExpression(leftArg: Expression, operator: ArithmeticOperator, rightArg: Expression)
    extends Expression {
  override def toSparql: String = s"""${leftArg.toSparql} $operator ${rightArg.toSparql}"""

  override def getVariables: Set[QueryVariable] = leftArg.getVariables ++ rightArg.getVariables
}

/**
 * Represents a regex function in a query (in a FILTER).
 *
 * @param textExpr the expression representing the text value or string literal to be checked against the provided pattern.
 * @param pattern  the REGEX pattern to be used.
 * @param modifier the modifier to be used.
 */
case class RegexFunction(textExpr: Expression, pattern: String, modifier: Option[String]) extends Expression {
  override def toSparql: String = modifier match {
    case Some(modifierStr) =>
      s"""regex(${textExpr.toSparql}, "$pattern", "$modifierStr")"""

    case None =>
      s"""regex(${textExpr.toSparql}, "$pattern")"""
  }

  override def getVariables: Set[QueryVariable] = textExpr.getVariables
}

/**
 * Represents a lang function in  a query (in a FILTER).
 *
 * @param textValueVar the variable representing the text value to be restricted to the specified language.
 */
case class LangFunction(textValueVar: QueryVariable) extends Expression {
  override def toSparql: String = s"""lang(${textValueVar.toSparql})"""

  override def getVariables: Set[QueryVariable] = Set(textValueVar)
}

/**
 * Represents the SPARQL `substr` function.
 *
 * @param textLiteralVar   the variable containing the string literal from which a substring is to be taken.
 * @param startExpression  an expression representing the 1-based index of the first character of the substring.
 * @param lengthExpression the length of the substring.
 */
case class SubStrFunction(textLiteralVar: QueryVariable, startExpression: Expression, lengthExpression: Expression)
    extends Expression {
  override def toSparql: String =
    s"""substr(${textLiteralVar.toSparql}, ${startExpression.toSparql}, ${lengthExpression.toSparql})"""

  override def getVariables: Set[QueryVariable] = Set(textLiteralVar)
}

/**
 * Represents the SPARQL `str` function.
 *
 * @param textLiteralVar the variable containing the string literal possibly with a language tag from which the string is to be taken.
 */
case class StrFunction(textLiteralVar: QueryVariable) extends Expression {
  override def toSparql: String = s"""str(${textLiteralVar.toSparql})"""

  override def getVariables: Set[QueryVariable] = Set(textLiteralVar)
}

/**
 * Represents a function call in a filter.
 *
 * @param functionIri the IRI of the function.
 * @param args        the arguments passed to the function.
 */
case class FunctionCallExpression(functionIri: IriRef, args: Seq[Entity]) extends Expression {
  override def toSparql: String = s"${functionIri.iri.toSparql}(${args.map(_.toSparql).mkString(", ")})"

  /**
   * Gets the argument at the given position as a [[QueryVariable]].
   * Throws a [[GravsearchException]] no argument exists at the given position or if it is not a [[QueryVariable]].
   *
   * @param pos the argument to be returned from [[args]].
   * @return a [[QueryVariable]].
   */
  def getArgAsQueryVar(pos: Int): QueryVariable = {

    if (args.size <= pos)
      throw GravsearchException(
        s"Not enough arguments given for call of $functionIri. ${args.size} are given, argument at position $pos is requested (0-based index)"
      )

    args(pos) match {
      case queryVar: QueryVariable => queryVar

      case other => throw GravsearchException(s"Variable required as function argument: $other")
    }

  }

  /**
   * Gets the argument at the given position as a [[XsdLiteral]] of the given datatype.
   * Throws a [[GravsearchException]] no argument exists at the given position or if it is not a [[XsdLiteral]] of the requested datatype.
   *
   * @param pos         the argument to be returned from [[args]].
   * @param xsdDatatype the argeument's datatype.
   * @return an [[XsdLiteral]].
   */
  def getArgAsLiteral(pos: Int, xsdDatatype: SmartIri): XsdLiteral = {

    if (args.size <= pos)
      throw GravsearchException(
        s"Not enough arguments given for call of $functionIri. ${args.size} are given, argument at position $pos is requested (0-based index)"
      )

    args(pos) match {
      case literal: XsdLiteral if literal.datatype == xsdDatatype => literal

      case other => throw GravsearchException(s"Literal of type $xsdDatatype required as function argument: $other")

    }

  }

  override def getVariables: Set[QueryVariable] = args.toSet.flatMap((arg: Entity) => arg.getVariables)
}

/**
 * Represents a FILTER pattern in a query.
 *
 * @param expression the expression in the FILTER.
 */
case class FilterPattern(expression: Expression) extends QueryPattern {
  override def toSparql: String = s"FILTER(${expression.toSparql})\n"
}

/**
 * Represents VALUES in a query.
 *
 * @param variable the variable that the values will be assigned to.
 * @param values   the IRIs that will be assigned to the variable.
 */
case class ValuesPattern(variable: QueryVariable, values: Set[IriRef]) extends QueryPattern {
  override def toSparql: String = s"VALUES ${variable.toSparql} { ${values.map(_.toSparql).mkString(" ")} }\n"
}

/**
 * Represents a UNION in the WHERE clause of a query.
 *
 * @param blocks the blocks of patterns contained in the UNION.
 */
case class UnionPattern(blocks: Seq[Seq[QueryPattern]]) extends QueryPattern {
  override def toSparql: String = {
    val blocksAsStrings = blocks.map { block: Seq[QueryPattern] =>
      val queryPatternStrings: Seq[String] = block.map { queryPattern: QueryPattern =>
        queryPattern.toSparql
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
  override def toSparql: String = {
    val queryPatternStrings: Seq[String] = patterns.map { queryPattern: QueryPattern =>
      queryPattern.toSparql
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
  override def toSparql: String = s"FILTER NOT EXISTS {\n ${patterns.map(_.toSparql).mkString}\n}\n"
}

/**
 * Represents a MINUS in a query.
 *
 * @param patterns the patterns contained in the MINUS.
 */
case class MinusPattern(patterns: Seq[QueryPattern]) extends QueryPattern {
  override def toSparql: String = s"MINUS {\n ${patterns.map(_.toSparql).mkString}\n}\n"
}

/**
 * Represents a CONSTRUCT clause in a query.
 *
 * @param statements  the statements in the CONSTRUCT clause.
 * @param querySchema if this is a Gravsearch query, represents the Knora API v2 ontology schema used in the query.
 */
case class ConstructClause(statements: Seq[StatementPattern], querySchema: Option[ApiV2Schema] = None)
    extends SparqlGenerator {
  override def toSparql: String = "CONSTRUCT {\n" + statements.map(_.toSparql).mkString + "} "
}

/**
 * Represents a WHERE clause in a query.
 *
 * @param patterns         the patterns in the WHERE clause.
 * @param positiveEntities if this is a Gravsearch query, contains the entities that are used in positive contexts
 *                         in the WHERE clause, i.e. not in MINUS or FILTER NOT EXISTS.
 * @param querySchema      if this is a Gravsearch query, represents the Knora API v2 ontology schema used in the query.
 */
case class WhereClause(
  patterns: Seq[QueryPattern],
  positiveEntities: Set[Entity] = Set.empty[Entity],
  querySchema: Option[ApiV2Schema] = None
) extends SparqlGenerator {
  override def toSparql: String = "WHERE {\n" + patterns.map(_.toSparql).mkString + "}\n"
}

/**
 * Represents a criterion to order by.
 *
 * @param queryVariable the variable used for ordering.
 * @param isAscending   indicates if the order is ascending or descending.
 */
case class OrderCriterion(queryVariable: QueryVariable, isAscending: Boolean) extends SparqlGenerator {
  override def toSparql: String =
    if (isAscending) {
      s"ASC(${queryVariable.toSparql})"
    } else {
      s"DESC(${queryVariable.toSparql})"
    }
}

/**
 * Represents a FROM clause.
 *
 * @param defaultGraph the graph to be used as the default graph in the query.
 */
case class FromClause(defaultGraph: IriRef) extends SparqlGenerator {
  override def toSparql: String = s"FROM ${defaultGraph.toSparql}\n"
}

/**
 * Represents a SPARQL CONSTRUCT query.
 *
 * @param constructClause the CONSTRUCT clause.
 * @param fromClause      the FROM clause, if any.
 * @param whereClause     the WHERE clause.
 * @param orderBy         the variables that the results should be ordered by.
 * @param offset          if this is a Gravsearch query, represents the OFFSET specified in the query.
 * @param querySchema     if this is a Gravsearch query, represents the Knora API v2 ontology schema used in the query.
 */
case class ConstructQuery(
  constructClause: ConstructClause,
  fromClause: Option[FromClause] = None,
  whereClause: WhereClause,
  orderBy: Seq[OrderCriterion] = Seq.empty[OrderCriterion],
  offset: Long = 0,
  querySchema: Option[ApiV2Schema] = None
) extends SparqlGenerator {
  override def toSparql: String = {
    val stringBuilder = new StringBuilder

    stringBuilder
      .append(constructClause.toSparql)
      .append(fromClause.map(_.toSparql).getOrElse(""))
      .append(whereClause.toSparql)

    if (orderBy.nonEmpty) {
      stringBuilder.append("ORDER BY ").append(orderBy.map(_.toSparql).mkString(" ")).append("\n")
    }

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
 * @param fromClause  the FROM clause, if any.
 * @param whereClause the WHERE clause.
 * @param orderBy     the variables that the results should be ordered by.
 * @param limit       the maximum number of result rows to be returned.
 * @param offset      the offset to be used (limit of the previous query + 1 to do paging).
 */
case class SelectQuery(
  variables: Seq[SelectQueryColumn],
  useDistinct: Boolean = true,
  fromClause: Option[FromClause] = None,
  whereClause: WhereClause,
  groupBy: Seq[QueryVariable] = Seq.empty[QueryVariable],
  orderBy: Seq[OrderCriterion] = Seq.empty[OrderCriterion],
  limit: Option[Int] = None,
  offset: Long = 0
) extends SparqlGenerator {
  override def toSparql: String = {
    val stringBuilder = new StringBuilder

    stringBuilder.append("SELECT ")

    if (useDistinct) {
      stringBuilder.append("DISTINCT ")

    }

    stringBuilder
      .append(variables.map(_.toSparql).mkString(" "))
      .append("\n")
      .append(fromClause.map(_.toSparql).getOrElse(""))
      .append(whereClause.toSparql)

    if (groupBy.nonEmpty) {
      stringBuilder.append("GROUP BY ").append(groupBy.map(_.toSparql).mkString(" ")).append("\n")
    }

    if (orderBy.nonEmpty) {
      stringBuilder.append("ORDER BY ").append(orderBy.map(_.toSparql).mkString(" ")).append("\n")
    }

    if (offset > 0) {
      stringBuilder.append("OFFSET ").append(offset).append("\n")
    }

    if (limit.nonEmpty) {
      stringBuilder.append(s"LIMIT ${limit.get}").append("\n")
    }

    stringBuilder.toString
  }
}
