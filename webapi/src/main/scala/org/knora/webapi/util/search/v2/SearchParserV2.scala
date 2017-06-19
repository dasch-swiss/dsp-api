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
import org.eclipse.rdf4j.query.MalformedQueryException
import org.knora.webapi.util.InputValidation
import org.knora.webapi.{OntologyConstants, _}

import scala.collection.JavaConverters._

/**
  * Represents something that can be the subject, predicate, or object of a triple pattern in a query.
  */
sealed trait Entity extends FilterExpression

/**
  * Represents a variable in a query.
  *
  * @param variableName the name of the variable.
  */
case class QueryVariable(variableName: String) extends Entity

/**
  * Represents an IRI in a query.
  *
  * @param iri the IRI.
  */
case class IriRef(iri: IRI) extends Entity {
    if (InputValidation.isInternalEntityIri(iri)) {
        throw SparqlSearchException(s"Internal ontology entity IRI not allowed in search query: $iri")
    }
}

/**
  * Represents a literal value with an XSD type.
  *
  * @param value    the literal value.
  * @param datatype the value's XSD type IRI.
  */
case class XsdLiteral(value: String, datatype: IRI) extends Entity

/**
  * Represents a statement pattern or block pattern in a query.
  */
sealed trait QueryPattern extends Ordered[QueryPattern]

/**
  * Represents a statement pattern in a query.
  *
  * @param subj the subject of the statement.
  * @param pred the predicate of the statement.
  * @param obj  the object of the statement.
  */
case class StatementPattern(subj: Entity, pred: Entity, obj: Entity) extends QueryPattern {
    override def compare(that: QueryPattern): Int = {
        that match {
            case _: StatementPattern => 0
            case _: UnionPattern => -1
            case _: OptionalPattern => -1
            case _: FilterPattern => -1
        }
    }
}

sealed trait FilterExpression

case class CompareExpression(leftArg: FilterExpression, operator: String, rightArg: FilterExpression) extends FilterExpression

case class AndExpression(leftArg: FilterExpression, rightArg: FilterExpression) extends FilterExpression

case class OrExpression(leftArg: FilterExpression, rightArg: FilterExpression) extends FilterExpression

/**
  * Represents a FILTER pattern in a query.
  *
  * @param expression the expression in the FILTER.
  */
case class FilterPattern(expression: FilterExpression) extends QueryPattern {
    override def compare(that: QueryPattern): Int = {
        that match {
            case _: StatementPattern => 1
            case _: UnionPattern => 1
            case _: OptionalPattern => 1
            case _: FilterPattern => 0
        }
    }
}

/**
  * Represents a UNION in the WHERE clause of a query.
  *
  * @param blocks the blocks of statement patterns contained in the UNION.
  */
case class UnionPattern(blocks: Seq[Seq[QueryPattern]]) extends QueryPattern {
    override def compare(that: QueryPattern): Int = {
        that match {
            case _: StatementPattern => 1
            case _: UnionPattern => 0
            case _: OptionalPattern => -1
            case _: FilterPattern => -1
        }
    }
}

/**
  * Represents an OPTIONAL in the WHERE clause of a query.
  *
  * @param statements the statements in the OPTIONAL block.
  */
case class OptionalPattern(statements: Seq[QueryPattern]) extends QueryPattern {
    override def compare(that: QueryPattern): Int = {
        that match {
            case _: StatementPattern => 1
            case _: UnionPattern => 1
            case _: OptionalPattern => 0
            case _: FilterPattern => -1
        }
    }
}

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
  * - The query must be a CONSTRUCT query.
  * - It must use no internal ontologies.
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

        val parsedQuery = try {
            sparqlParser.parseQuery(query, OntologyConstants.KnoraApi.KnoraApiOntologyIri + OntologyConstants.KnoraApiV2Simplified.VersionSegment + "#")
        } catch {
            case malformed: MalformedQueryException => throw SparqlSearchException(s"Invalid search query: ${malformed.getMessage}")
        }

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
            val constructStatements: Seq[StatementPattern] = constructStatementsWithConstants.toVector.map {
                constructStatementWithConstant =>
                    StatementPattern(
                        subj = makeEntity(nameToVar(constructStatementWithConstant.subj)),
                        pred = makeEntity(nameToVar(constructStatementWithConstant.pred)),
                        obj = makeEntity(nameToVar(constructStatementWithConstant.obj))
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
            wherePatterns.sorted.toVector
        }

        private def unsupported(node: QueryModelNode) {
            throw SparqlSearchException(s"SPARQL feature not supported in search query: $node")
        }

        override def meet(node: Slice): Unit = {
            unsupported(node)
        }

        /**
          * Converts an RDF4J [[Var]] into a [[Entity]].
          *
          * @param objVar the [[Var]] to be converted.
          * @return a [[Entity]].
          */
        private def makeEntity(objVar: Var): Entity = {
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
            val subj: Entity = makeEntity(node.getSubjectVar)
            val pred: Entity = makeEntity(node.getPredicateVar)
            val obj: Entity = makeEntity(node.getObjectVar)
            wherePatterns.append(StatementPattern(subj = subj, pred = pred, obj = obj))
        }

        override def meet(node: Str): Unit = {
            unsupported(node)
        }

        override def meet(node: Sum): Unit = {
            unsupported(node)
        }

        /**
          * Checks the contents of a block patterns to prevent nested blocks.
          *
          * @param patterns the patterns inside the block.
          * @return the same patterns.
          */
        private def checkBlockPatterns(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
            for (pattern <- patterns) {
                pattern match {
                    case _: UnionPattern | _: OptionalPattern => throw SparqlSearchException("Nested blocks are not allowed in search queries")
                    case _ => ()
                }
            }

            patterns
        }

        override def meet(node: Union): Unit = {
            // Get the block of query patterns on the left side of the UNION.
            val leftPatterns: Seq[QueryPattern] = node.getLeftArg match {
                case _: Union => throw SparqlSearchException("Nested UNIONs are not allowed in search queries")
                case otherLeftArg =>
                    val leftArgVisitor = new SimpleConstructQueryModelVisitor
                    otherLeftArg.visit(leftArgVisitor)
                    checkBlockPatterns(leftArgVisitor.getOrderedWherePatterns)
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
                    Seq(checkBlockPatterns(rightArgVisitor.getOrderedWherePatterns))
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
                val sourceName: String = projectionElem.getSourceName
                val targetName: String = projectionElem.getTargetName

                if (sourceName == targetName) {
                    throw SparqlSearchException(s"SELECT queries are not allowed in search, please use a CONSTRUCT query instead")
                }

                projectionElem.getTargetName match {
                    case "subject" => subj = Some(sourceName)
                    case "predicate" => pred = Some(sourceName)
                    case "object" => obj = Some(sourceName)
                    case _ => SparqlSearchException(s"SELECT queries are not allowed in search, please use a CONSTRUCT query instead")
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
            node.visitChildren(this)
        }

        override def meet(node: OrderElem): Unit = {
            unsupported(node)
        }

        override def meet(node: Order): Unit = {
            unsupported(node)
        }

        override def meet(node: Or): Unit = {
            // Does nothing, because this is handled in meet(node: Filter).
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
            // Does nothing, because this is handled in meet(node: Filter).
        }

        override def meet(add: Add): Unit = {
            unsupported(add)
        }

        override def meet(node: QueryRoot): Unit = {
            node.visitChildren(this)
        }

        override def meet(node: DescribeOperator): Unit = {
            throw SparqlSearchException(s"DESCRIBE queries are not allowed in search, please use a CONSTRUCT query instead")
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
            def makeFilterExpression(valueExpr: ValueExpr): FilterExpression = {
                valueExpr match {
                    case compare: Compare =>
                        val leftArg = makeFilterExpression(compare.getLeftArg)
                        val rightArg = makeFilterExpression(compare.getRightArg)
                        val operator = compare.getOperator.getSymbol

                        CompareExpression(
                            leftArg = leftArg,
                            operator = operator,
                            rightArg = rightArg
                        )

                    case and: And =>
                        val leftArg = makeFilterExpression(and.getLeftArg)
                        val rightArg = makeFilterExpression(and.getRightArg)

                        AndExpression(
                            leftArg = leftArg,
                            rightArg = rightArg
                        )

                    case or: Or =>
                        val leftArg = makeFilterExpression(or.getLeftArg)
                        val rightArg = makeFilterExpression(or.getRightArg)

                        OrExpression(
                            leftArg = leftArg,
                            rightArg = rightArg
                        )

                    case valueConstant: ValueConstant =>
                        valueConstant.getValue match {
                            case literal: rdf4j.model.Literal => XsdLiteral(value = literal.stringValue, datatype = literal.getDatatype.stringValue)
                            case iri: org.eclipse.rdf4j.model.IRI => IriRef(iri = iri.toString)
                            case other => throw SparqlSearchException(s"Unsupported ValueConstant: $other with class ${other.getClass.getName}")
                        }

                    case sparqlVar: Var => makeEntity(sparqlVar)

                    case other => throw SparqlSearchException(s"Unsupported FILTER expression: $other")
                }
            }

            val filterExpression: FilterExpression = makeFilterExpression(node.getCondition)
            wherePatterns.append(FilterPattern(expression = filterExpression))

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
            val optionalVisitor = new SimpleConstructQueryModelVisitor
            node.visitChildren(optionalVisitor)
            wherePatterns.append(OptionalPattern(checkBlockPatterns(optionalVisitor.getOrderedWherePatterns)))
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