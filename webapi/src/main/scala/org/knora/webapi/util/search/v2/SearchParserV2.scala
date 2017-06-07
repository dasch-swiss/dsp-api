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

package org.knora.webapi.util.search.v2

import org.eclipse.rdf4j
import org.eclipse.rdf4j.query.algebra
import org.eclipse.rdf4j.query.algebra._
import org.eclipse.rdf4j.query.parser.ParsedQuery
import org.eclipse.rdf4j.query.parser.sparql._
import org.knora.webapi.{OntologyConstants, _}

import scala.collection.JavaConverters._

/**
  * Represents something that can be the subject of a triple pattern in a query.
  */
sealed trait StatementPatternSubject

/**
  * Represents something that can be the object of a triple pattern in a query.
  */
sealed trait StatementPatternObject

/**
  * Represents a variable in a query.
  *
  * @param variableName the name of the variable.
  */
case class QueryVariable(variableName: String) extends StatementPatternSubject with StatementPatternObject

/**
  * Represents an IRI in a query.
  *
  * @param iri the IRI.
  */
case class IriRef(iri: IRI) extends StatementPatternSubject with StatementPatternObject

/**
  * Represents a literal value with an XSD type.
  *
  * @param value    the literal value.
  * @param datatype the value's XSD type IRI.
  */
case class XsdLiteral(value: String, datatype: IRI) extends StatementPatternObject

/**
  * Represents a statement pattern or block pattern in a query.
  */
sealed trait QueryPattern

/**
  * Represents a statement pattern in a query.
  *
  * @param subj the subject of the statement.
  * @param pred the predicate of the statement.
  * @param obj  the object of the statement.
  */
case class StatementPattern(subj: StatementPatternSubject, pred: IriRef, obj: StatementPatternObject) extends QueryPattern

/**
  * Represents a FILTER pattern in a query.
  *
  * @param leftArgVariableName the left argument of the FILTER condition, which must be a variable name.
  * @param operator            the string representation of the FILTER condition's operator.
  * @param rightArgLiteral     the right argument of the FILTER condition, which must be a literal.
  */
case class FilterPattern(leftArgVariableName: String, operator: String, rightArgLiteral: XsdLiteral) extends QueryPattern

/**
  * Represents a block pattern in a query, such as a UNION.
  */
sealed trait BlockPattern

/**
  * Represents a UNION in the WHERE clause of a query.
  *
  * @param blocks the blocks of statement patterns contained in the UNION.
  */
case class UnionPattern(blocks: Seq[Seq[QueryPattern]]) extends QueryPattern

/**
  * Represents a simple CONSTRUCT clause in a query.
  *
  * @param statements the statements in the CONSTRUCT clause.
  */
case class SimpleConstructClause(statements: Seq[StatementPattern])

/**
  * Represents a simple WHERE clause in a query.
  *
  * @param statements the statements in the WHERE clause.
  */
case class SimpleWhereClause(statements: Seq[QueryPattern])

/**
  * Represents a simple CONSTRUCT query submitted to the Knora API.
  *
  * @param constructClause the CONSTRUCT clause.
  * @param whereClause     the WHERE clause.
  */
case class SimpleConstructQuery(constructClause: SimpleConstructClause, whereClause: SimpleWhereClause)


/**
  * Parses the client's SPARQL query for an extended search in API v2. The SPARQL that is accepted is restricted:
  *
  * - The query must be a CONSTRUCT query, using no internal ontologies. (TODO: check this.)
  * - The CONSTRUCT clause may contain only triple patterns.
  * - The WHERE clause may contain only triple patterns, FILTER, and UNION.
  * - No function calls are allowed.
  * - A UNION may not contain a nested UNION.
  * - The condition of a FILTER must have a variable as the left argument and a literal as the right argument.
  */
object SearchParserV2 {
    // This implementation uses the RDF4J SPARQL parser.
    private val sparqlParserFactory = new SPARQLParserFactory()
    private val sparqlParser = sparqlParserFactory.getParser

    /**
      * Given a string representation of a simple SPARQL CONSTRUCT query, returns a [[SimpleConstructQuery]].
      *
      * @param query the SPARQL string to be parsed.
      * @return a [[SimpleConstructQuery]].
      */
    def parseSearchQuery(query: String): SimpleConstructQuery = {
        val visitor = new SimpleConstructQueryModelVisitor
        val parsedQuery = sparqlParser.parseQuery(query, OntologyConstants.KnoraApi.KnoraApiOntologyIri + OntologyConstants.KnoraApiV2Simplified.VersionSegment + "#")
        parsedQuery.getTupleExpr.visit(visitor)
        visitor.makeSimpleConstructQuery
    }

    /**
      * An RDF4J [[QueryModelVisitor]] that converts a [[ParsedQuery]] into a [[SimpleConstructQuery]].
      */
    class SimpleConstructQueryModelVisitor extends QueryModelVisitor[SparqlSearchException] {

        // Represents a statement pattern in the CONSTRUCT clause. Each string could be a variable name or a parser-generated
        // constant. These constants can be replaced by their values only after valueConstants is populated.
        private case class ConstructStatementWithConstants(subj: String, pred: String, obj: String)

        // A map of parser-generated constants to literal values.
        private val valueConstants: collection.mutable.Map[String, ValueConstant] = collection.mutable.Map.empty[String, ValueConstant]

        // A sequence of statement patterns in the CONSTRUCT clause, possibly using parser-generated constants.
        private val constructStatementsWithConstants: collection.mutable.ArrayBuffer[ConstructStatementWithConstants] = collection.mutable.ArrayBuffer.empty[ConstructStatementWithConstants]

        // A sequence of statement patterns in the WHERE clause.
        private val wherePatterns: collection.mutable.ArrayBuffer[QueryPattern] = collection.mutable.ArrayBuffer.empty[QueryPattern]

        /**
          * After this visitor has visited the parse tree, this method returns a [[SimpleConstructQuery]] representing
          * the query that was parsed.
          *
          * @return a [[SimpleConstructQuery]].
          */
        def makeSimpleConstructQuery: SimpleConstructQuery = {
            /**
              * Given a source name used in a [[ProjectionElem]], checks whether it's the name of a constant whose
              * literal value was saved when the [[ExtensionElem]] nodes were processed. If so, returns a [[Var]] representing
              * the literal value. Otherwise, returns a [[Var]] representing the name itself. The resulting [[Var]] can be
              * passed to `makeStatementPatternSubject`, `makeStatementPatternPredicate`, or `makeStatementPatternObject`.
              *
              * @param sourceName the source name.
              * @return a [[Var]] representing the name or its literal value.
              */
            def nameToVar(sourceName: String): Var = {
                val sparqlVar = new Var
                sparqlVar.setName(sourceName)

                valueConstants.get(sourceName) match {
                    case Some(valueConstant) =>
                        sparqlVar.setConstant(true)
                        sparqlVar.setValue(valueConstant.getValue)

                    case None =>
                        sparqlVar.setConstant(false)
                }

                sparqlVar
            }


            // Convert each ConstructStatementWithConstants to a StatementPattern for use in the CONSTRUCT clause.
            val constructStatements: Seq[StatementPattern] = constructStatementsWithConstants.map {
                constructStatementWithConstant =>
                    StatementPattern(
                        subj = makeStatementPatternSubject(nameToVar(constructStatementWithConstant.subj)),
                        pred = makeStatementPatternPredicate(nameToVar(constructStatementWithConstant.pred)),
                        obj = makeStatementPatternObject(nameToVar(constructStatementWithConstant.obj))
                    )
            }

            SimpleConstructQuery(
                constructClause = SimpleConstructClause(statements = constructStatements),
                whereClause = SimpleWhereClause(statements = getOrderedWherePatterns)
            )
        }

        /**
          * Reorders the patterns in the WHERE clause by putting the FILTERs at the end.
          */
        private def getOrderedWherePatterns: Seq[QueryPattern] = {
            val (filters, nonFilters) = wherePatterns.partition {
                case _: FilterPattern => true
                case _ => false
            }

            val orderedPatterns = collection.mutable.ArrayBuffer.empty[QueryPattern]
            orderedPatterns.appendAll(nonFilters)
            orderedPatterns.appendAll(filters)
            orderedPatterns
        }

        private def unsupported(node: QueryModelNode) {
            throw SparqlSearchException(s"Unsupported SPARQL feature: $node")
        }

        override def meet(node: Slice): Unit = {
            unsupported(node)
        }

        /**
          * Converts an RDF4J [[Var]] into a [[StatementPatternSubject]].
          *
          * @param subjVar the [[Var]] to be converted.
          * @return a [[StatementPatternSubject]].
          */
        private def makeStatementPatternSubject(subjVar: Var): StatementPatternSubject = {
            // The subject of a statement pattern must be an IRI or a variable.

            if (subjVar.isAnonymous || subjVar.isConstant) {
                subjVar.getValue match {
                    case iri: rdf4j.model.IRI => IriRef(iri.stringValue)
                    case other => throw SparqlSearchException(s"Invalid subject for triple pattern: $other")
                }
            } else {
                QueryVariable(subjVar.getName)
            }
        }

        /**
          * Converts an RDF4J [[Var]] into a [[IriRef]] representing the predicate of a statement pattern.
          *
          * @param predVar the [[Var]] to be converted.
          * @return an [[IriRef]].
          */
        private def makeStatementPatternPredicate(predVar: Var): IriRef = {
            // The predicate of a statement pattern must be an IRI.

            if (predVar.isAnonymous || predVar.isConstant) {
                predVar.getValue match {
                    case iri: rdf4j.model.IRI => IriRef(iri.stringValue)
                    case other => throw SparqlSearchException(s"Invalid predicate for triple pattern: $other")
                }
            } else {
                throw SparqlSearchException(s"Invalid predicate for triple pattern: ${predVar.getValue}")
            }
        }

        /**
          * Converts an RDF4J [[Var]] into a [[StatementPatternObject]].
          *
          * @param objVar the [[Var]] to be converted.
          * @return a [[StatementPatternObject]].
          */
        private def makeStatementPatternObject(objVar: Var): StatementPatternObject = {
            // The object of a statement pattern must be an IRI, a variable, or a literal.

            if (objVar.isAnonymous || objVar.isConstant) {
                objVar.getValue match {
                    case iri: rdf4j.model.IRI => IriRef(iri.stringValue)
                    case literal: rdf4j.model.Literal => XsdLiteral(value = literal.stringValue, datatype = literal.getDatatype.stringValue)
                    case other => throw SparqlSearchException(s"Invalid object for triple pattern: $other")
                }
            } else {
                QueryVariable(objVar.getName)
            }
        }

        override def meet(node: algebra.StatementPattern): Unit = {
            val subj: StatementPatternSubject = makeStatementPatternSubject(node.getSubjectVar)
            val pred: IriRef = makeStatementPatternPredicate(node.getPredicateVar)
            val obj: StatementPatternObject = makeStatementPatternObject(node.getObjectVar)
            wherePatterns.append(StatementPattern(subj = subj, pred = pred, obj = obj))
        }

        override def meet(node: Str): Unit = {
            unsupported(node)
        }

        override def meet(node: Sum): Unit = {
            unsupported(node)
        }

        override def meet(node: Union): Unit = {
            // Check query patterns to prevent nested UNIONs.
            def checkPatterns(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
                for (pattern <- patterns) {
                    pattern match {
                        case _: UnionPattern => throw SparqlSearchException("Nested UNIONs are not allowed")
                        case _ => ()
                    }
                }

                patterns
            }

            // Get the block of query patterns on the left side of the UNION.
            val leftPatterns: Seq[QueryPattern] = node.getLeftArg match {
                case _: Union => throw SparqlSearchException("Nested UNIONs are not allowed")
                case otherLeftArg =>
                    val leftArgVisitor = new SimpleConstructQueryModelVisitor
                    otherLeftArg.visit(leftArgVisitor)
                    checkPatterns(leftArgVisitor.getOrderedWherePatterns)
            }

            // Get the block(s) of query patterns on the right side of the UNION.
            val rightPatterns: Seq[Seq[QueryPattern]] = node.getRightArg match {
                case rightArgUnion: Union =>
                    // If the right arg is also a UNION, recursively get its blocks. This represents a sequence of
                    // UNIONs rather than a nested UNION.
                    val rightArgVisitor = new SimpleConstructQueryModelVisitor
                    rightArgUnion.visit(rightArgVisitor)
                    val rightWherePatterns = rightArgVisitor.getOrderedWherePatterns

                    if (rightWherePatterns.size > 1) {
                        throw SparqlSearchException(s"Right argument of UNION is not a UnionPattern as expected: $rightWherePatterns")
                    }

                    rightWherePatterns.head match {
                        case rightUnionPattern: UnionPattern => rightUnionPattern.blocks
                        case other => throw SparqlSearchException(s"Right argument of UNION is not a UnionPattern as expected: $other")
                    }

                case otherRightArg =>
                    val rightArgVisitor = new SimpleConstructQueryModelVisitor
                    otherRightArg.visit(rightArgVisitor)
                    Seq(checkPatterns(rightArgVisitor.getOrderedWherePatterns))
            }

            wherePatterns.append(UnionPattern(Seq(leftPatterns) ++ rightPatterns))
        }

        override def meet(node: ValueConstant): Unit = {
            unsupported(node)
        }

        override def meet(node: ListMemberOperator): Unit = {
            unsupported(node)
        }

        override def meet(node: Var): Unit = {
            unsupported(node)
        }

        override def meet(node: ZeroLengthPath): Unit = {
            unsupported(node)
        }

        override def meet(node: Regex): Unit = {
            unsupported(node)
        }

        override def meet(node: Reduced): Unit = {
            node.visitChildren(this)
        }

        override def meet(node: ProjectionElemList): Unit = {
            // A ProjectionElemList represents the patterns in the CONSTRUCT clause. They're represented using
            // parser-generated constants instead of literal values, so for now we just have to store them that way
            // for now. Later, once we have the values of the constants, we will be able to build the CONSTRUCT clause.

            var subj: Option[String] = None
            var pred: Option[String] = None
            var obj: Option[String] = None

            for (projectionElem: ProjectionElem <- node.getElements.asScala) {
                val sourceName = projectionElem.getSourceName

                projectionElem.getTargetName match {
                    case "subject" => subj = Some(sourceName)
                    case "predicate" => pred = Some(sourceName)
                    case "object" => obj = Some(sourceName)
                }
            }

            if (subj.isEmpty || pred.isEmpty || obj.isEmpty) {
                throw SparqlSearchException(s"Incomplete ProjectionElemList: $node")
            }

            constructStatementsWithConstants.append(ConstructStatementWithConstants(subj = subj.get, pred = pred.get, obj = obj.get))
        }

        override def meet(node: ProjectionElem): Unit = {
            unsupported(node)
        }

        override def meet(node: Projection): Unit = {
            unsupported(node)
        }

        override def meet(node: OrderElem): Unit = {
            unsupported(node)
        }

        override def meet(node: Order): Unit = {
            unsupported(node)
        }

        override def meet(node: Or): Unit = {
            unsupported(node)
        }

        override def meet(node: Not): Unit = {
            unsupported(node)
        }

        override def meet(node: Namespace): Unit = {
            unsupported(node)
        }

        override def meet(node: MultiProjection): Unit = {
            node.visitChildren(this)
        }

        override def meet(move: Move): Unit = {
            unsupported(move)
        }

        override def meet(node: Coalesce): Unit = {
            unsupported(node)
        }

        override def meet(node: Compare): Unit = {
            // Do nothing, because this is handled by meet(node: Filter).
        }

        override def meet(node: CompareAll): Unit = {
            unsupported(node)
        }

        override def meet(node: IsLiteral): Unit = {
            unsupported(node)
        }

        override def meet(node: IsNumeric): Unit = {
            unsupported(node)
        }

        override def meet(node: IsResource): Unit = {
            unsupported(node)
        }

        override def meet(node: IsURI): Unit = {
            unsupported(node)
        }

        override def meet(node: SameTerm): Unit = {
            unsupported(node)
        }

        override def meet(modify: Modify): Unit = {
            unsupported(modify)
        }

        override def meet(node: Min): Unit = {
            unsupported(node)
        }

        override def meet(node: Max): Unit = {
            unsupported(node)
        }

        override def meet(node: ExtensionElem): Unit = {
            // An ExtensionElem provides mappings between parser-generated constants and literal values that are used
            // in the CONSTRUCT clause. We need to save these so we can build the CONSTRUCT clause correctly.

            node.getExpr match {
                case valueConstant: ValueConstant => valueConstants.put(node.getName, valueConstant)
                case _ => ()
            }
        }

        override def meet(node: Extension): Unit = {
            node.visitChildren(this)
        }

        override def meet(node: Exists): Unit = {
            unsupported(node)
        }

        override def meet(node: EmptySet): Unit = {
            unsupported(node)
        }

        override def meet(node: Distinct): Unit = {
            unsupported(node)
        }

        override def meet(node: Difference): Unit = {
            unsupported(node)
        }

        override def meet(deleteData: DeleteData): Unit = {
            unsupported(deleteData)
        }

        override def meet(node: Datatype): Unit = {
            unsupported(node)
        }

        override def meet(clear: Clear): Unit = {
            unsupported(clear)
        }

        override def meet(node: Bound): Unit = {
            unsupported(node)
        }

        override def meet(node: BNodeGenerator): Unit = {
            unsupported(node)
        }

        override def meet(node: BindingSetAssignment): Unit = {
            unsupported(node)
        }

        override def meet(node: Avg): Unit = {
            unsupported(node)
        }

        override def meet(node: ArbitraryLengthPath): Unit = {
            unsupported(node)
        }

        override def meet(node: And): Unit = {
            unsupported(node)
        }

        override def meet(add: Add): Unit = {
            unsupported(add)
        }

        override def meet(node: QueryRoot): Unit = {
            node.visitChildren(this)
        }

        override def meet(node: DescribeOperator): Unit = {
            unsupported(node)
        }

        override def meet(copy: Copy): Unit = {
            unsupported(copy)
        }

        override def meet(node: Count): Unit = {
            unsupported(node)
        }

        override def meet(create: Create): Unit = {
            unsupported(create)
        }

        override def meet(node: Sample): Unit = {
            unsupported(node)
        }

        override def meet(node: Service): Unit = {
            unsupported(node)
        }

        override def meet(node: SingletonSet): Unit = {
            unsupported(node)
        }

        override def meet(node: CompareAny): Unit = {
            unsupported(node)
        }

        override def meet(node: Filter): Unit = {
            // The left argument of a FILTER must be a variable name, and the right argument must be a literal.

            val condition: Compare = node.getCondition match {
                case compare: Compare => compare
                case other => throw SparqlSearchException(s"Unsupported FILTER condition: $other")
            }

            val leftArgVariableName: String = condition.getLeftArg match {
                case sparqlVar: Var =>
                    if (sparqlVar.isAnonymous || sparqlVar.isConstant) {
                        throw SparqlSearchException(s"Left argument of FILTER condition is not a variable: $sparqlVar")
                    }

                    sparqlVar.getName

                case other => throw SparqlSearchException(s"Left argument of FILTER condition is not a variable: $other")
            }

            val operator: String = condition.getOperator.getSymbol

            val rightArgLiteral: XsdLiteral = condition.getRightArg match {
                case valueConstant: ValueConstant =>
                    valueConstant.getValue match {
                        case literal: rdf4j.model.Literal => XsdLiteral(value = literal.stringValue, datatype = literal.getDatatype.stringValue)
                        case other => throw SparqlSearchException(s"Right argument of FILTER condition is not a literal: $other")
                    }
            }

            wherePatterns.append(
                FilterPattern(
                    leftArgVariableName = leftArgVariableName,
                    operator = operator,
                    rightArgLiteral = rightArgLiteral
                )
            )

            node.visitChildren(this)
        }

        override def meet(node: FunctionCall): Unit = {
            unsupported(node)
        }

        override def meet(node: Group): Unit = {
            unsupported(node)
        }

        override def meet(node: GroupConcat): Unit = {
            unsupported(node)
        }

        override def meet(node: GroupElem): Unit = {
            unsupported(node)
        }

        override def meet(node: If): Unit = {
            unsupported(node)
        }

        override def meet(node: In): Unit = {
            unsupported(node)
        }

        override def meet(insertData: InsertData): Unit = {
            unsupported(insertData)
        }

        override def meet(node: Intersection): Unit = {
            unsupported(node)
        }

        override def meet(node: IRIFunction): Unit = {
            unsupported(node)
        }

        override def meet(node: IsBNode): Unit = {
            unsupported(node)
        }

        override def meet(node: MathExpr): Unit = {
            unsupported(node)
        }

        override def meet(node: LocalName): Unit = {
            unsupported(node)
        }

        override def meet(load: Load): Unit = {
            unsupported(load)
        }

        override def meet(node: Like): Unit = {
            unsupported(node)
        }

        override def meet(node: LeftJoin): Unit = {
            unsupported(node)
        }

        override def meet(node: LangMatches): Unit = {
            unsupported(node)
        }

        override def meet(node: Lang): Unit = {
            unsupported(node)
        }

        override def meet(node: Label): Unit = {
            unsupported(node)
        }

        override def meet(node: Join): Unit = {
            // Successive statements are connected by Joins.
            node.visitChildren(this)
        }

        override def meetOther(node: QueryModelNode): Unit = {
            unsupported(node)
        }
    }

}