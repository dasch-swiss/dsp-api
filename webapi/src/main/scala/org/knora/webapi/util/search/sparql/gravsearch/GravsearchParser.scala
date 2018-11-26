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

package org.knora.webapi.util.search.sparql.gravsearch

import org.eclipse.rdf4j
import org.eclipse.rdf4j.query.parser.sparql._
import org.eclipse.rdf4j.query.parser.{ParsedQuery, QueryParser}
import org.eclipse.rdf4j.query.{MalformedQueryException, algebra}
import org.knora.webapi._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.search._
import org.knora.webapi.util.{SmartIri, StringFormatter, search}

import scala.collection.JavaConverters._

/**
  * Parses a Gravsearch query. The syntax that is accepted is that of a SPARQL CONSTRUCT query, with some restrictions:
  *
  * - The query must be a CONSTRUCT query.
  * - It must use no internal ontologies.
  * - The CONSTRUCT clause may contain only quad patterns.
  * - The WHERE clause may contain only quad patterns, FILTER, and UNION.
  * - A UNION may not contain a nested UNION or OPTIONAL.
  * - The value assigned in a BIND must be a Knora data IRI.
  */
object GravsearchParser {
    // This implementation uses the RDF4J SPARQL parser.
    private val sparqlParserFactory = new SPARQLParserFactory()
    private val sparqlParser: QueryParser = sparqlParserFactory.getParser

    /**
      * Given a string representation of a Gravsearch query, returns a [[ConstructQuery]].
      *
      * @param query the Gravsearch string to be parsed.
      * @return a [[ConstructQuery]].
      */
    def parseQuery(query: String): ConstructQuery = {
        val visitor = new ConstructQueryModelVisitor

        val parsedQuery = try {
            sparqlParser.parseQuery(query, OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion)
        } catch {
            case malformed: MalformedQueryException => throw GravsearchException(s"Invalid search query: ${malformed.getMessage}")
        }

        parsedQuery.getTupleExpr.visit(visitor)
        visitor.makeConstructQuery
    }

    /**
      * An RDF4J [[algebra.QueryModelVisitor]] that converts a [[ParsedQuery]] into a [[ConstructQuery]].
      *
      * @param isInNegation Indicates if the element currently processed is in a context of negation (FILTER NOT EXISTS or MINUS).
      */
    class ConstructQueryModelVisitor(isInNegation: Boolean = false) extends algebra.QueryModelVisitor[GravsearchException] {
        private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // Represents a statement pattern in the CONSTRUCT clause. Each string could be a variable name or a parser-generated
        // constant. These constants can be replaced by their values only after valueConstants is populated.
        private case class ConstructStatementWithConstants(subj: String, pred: String, obj: String)

        // A map of parser-generated constants to literal values.
        private val valueConstants: collection.mutable.Map[String, algebra.ValueConstant] = collection.mutable.Map.empty[String, algebra.ValueConstant]

        // A sequence of statement patterns in the CONSTRUCT clause, possibly using parser-generated constants.
        private val constructStatementsWithConstants: collection.mutable.ArrayBuffer[ConstructStatementWithConstants] = collection.mutable.ArrayBuffer.empty[ConstructStatementWithConstants]

        // A sequence of statement patterns in the WHERE clause.
        private val wherePatterns: collection.mutable.ArrayBuffer[QueryPattern] = collection.mutable.ArrayBuffer.empty[QueryPattern]

        // A sequence of `OrderCriterion` representing the Order By statement.
        private val orderBy: collection.mutable.ArrayBuffer[OrderCriterion] = collection.mutable.ArrayBuffer.empty[OrderCriterion]

        // The OFFSET specified in the input query.
        private var offset: Long = 0

        // Entities mentioned positively (i.e. not only in a FILTER NOT EXISTS or MINUS) in the WHERE clause.
        private val positiveEntities: collection.mutable.Set[Entity] = collection.mutable.Set.empty[Entity]

        // A set of all IRIs found by the visitor. Used to determine whether the Knora API v2 complex schema is used.
        private val allIris: collection.mutable.Set[SmartIri] = collection.mutable.Set.empty[SmartIri]

        /**
          * After this visitor has visited the parse tree, this method returns a [[ConstructQuery]] representing
          * the query that was parsed.
          *
          * @return a [[ConstructQuery]].
          */
        def makeConstructQuery: ConstructQuery = {
            /**
              * Given a source name used in an [[algebra.ProjectionElem]], checks whether it's the name of a constant whose
              * literal value was saved when the [[algebra.ExtensionElem]] nodes were processed. If so, returns a [[algebra.Var]] representing
              * the literal value. Otherwise, returns an [[algebra.Var]] representing the name itself. The resulting [[algebra.Var]] can be
              * passed to `makeStatementPatternSubject`, `makeStatementPatternPredicate`, or `makeStatementPatternObject`.
              *
              * @param sourceName the source name.
              * @return an [[algebra.Var]] representing the name or its literal value.
              */
            def nameToVar(sourceName: String): algebra.Var = {
                val sparqlVar = new algebra.Var
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
            val constructStatements: Seq[search.StatementPattern] = constructStatementsWithConstants.toVector.map {
                (constructStatementWithConstant: ConstructStatementWithConstants) =>
                    StatementPattern(
                        subj = makeEntityFromVar(nameToVar(constructStatementWithConstant.subj)),
                        pred = makeEntityFromVar(nameToVar(constructStatementWithConstant.pred)),
                        obj = makeEntityFromVar(nameToVar(constructStatementWithConstant.obj)),
                        namedGraph = None
                    )
            }

            val querySchema = if (allIris.exists(_.getOntologySchema.contains(ApiV2WithValueObjects))) {
                ApiV2WithValueObjects
            } else {
                ApiV2Simple
            }

            ConstructQuery(
                constructClause = ConstructClause(statements = constructStatements, querySchema = Some(querySchema)),
                whereClause = WhereClause(patterns = getWherePatterns, positiveEntities = positiveEntities.toSet, querySchema = Some(querySchema)),
                orderBy = orderBy,
                offset = offset,
                querySchema = Some(querySchema)
            )
        }

        /**
          * Returns the WHERE patterns found in the query.
          */
        private def getWherePatterns: Seq[QueryPattern] = {
            wherePatterns
        }

        private def unsupported(node: algebra.QueryModelNode) {
            throw GravsearchException(s"SPARQL feature not supported in Gravsearch query: $node")
        }

        private def checkIriSchema(smartIri: SmartIri): Unit = {
            if (smartIri.isKnoraOntologyIri) {
                throw GravsearchException(s"Ontology IRI not allowed in Gravsearch query: $smartIri")
            }

            if (smartIri.isKnoraEntityIri) {
                smartIri.getOntologySchema match {
                    case Some(_: ApiV2Schema) => allIris.add(smartIri)
                    case _ => throw GravsearchException(s"Ontology schema not allowed in Gravsearch query: $smartIri")
                }
            }
        }

        private def makeIri(rdf4jIri: rdf4j.model.IRI): IriRef = {
            val smartIri: SmartIri = rdf4jIri.stringValue.toSmartIriWithErr(throw GravsearchException(s"Invalid IRI: ${rdf4jIri.stringValue}"))
            checkIriSchema(smartIri)
            val iriRef = IriRef(smartIri)

            if (!isInNegation) {
                positiveEntities += iriRef
            }

            iriRef
        }

        override def meet(node: algebra.Slice): Unit = {
            if (node.hasLimit) {
                throw GravsearchException("LIMIT not supported in Gravsearch query")
            }

            node.getArg.visit(this)

            if (node.hasOffset) {
                offset = node.getOffset
            } else {
                throw GravsearchException(s"Invalid OFFSET: ${node.getOffset}")
            }
        }

        /**
          * Converts an RDF4J [[algebra.Var]] into an [[Entity]].
          *
          * @param objVar the [[algebra.Var]] to be converted.
          * @return a [[Entity]].
          */
        private def makeEntityFromVar(objVar: algebra.Var): Entity = {
            if (objVar.isAnonymous || objVar.isConstant) {
                makeEntityFromValue(objVar.getValue)
            } else {
                makeQueryVariable(objVar.getName)
            }
        }

        /**
          * Converts an [[rdf4j.model.Value]] into an [[Entity]].
          *
          * @param value the value to be converted.
          * @return a [[Entity]].
          */
        private def makeEntityFromValue(value: rdf4j.model.Value): Entity = {
            value match {
                case iri: rdf4j.model.IRI => makeIri(iri)

                case literal: rdf4j.model.Literal =>
                    val datatype = literal.getDatatype.stringValue.toSmartIriWithErr(throw GravsearchException(s"Invalid datatype: ${literal.getDatatype.stringValue}"))
                    checkIriSchema(datatype)
                    XsdLiteral(value = literal.stringValue, datatype = datatype)

                case other => throw GravsearchException(s"Unsupported Value: $other with class ${other.getClass.getName}")
            }
        }

        private def makeQueryVariable(variableName: String): QueryVariable = {
            val queryVar = if (variableName.contains("__")) {
                throw GravsearchException(s"Invalid variable name: $variableName")
            } else {
                QueryVariable(variableName)
            }

            if (!isInNegation) {
                positiveEntities += queryVar
            }

            queryVar
        }

        override def meet(node: algebra.StatementPattern): Unit = {
            val subj: Entity = makeEntityFromVar(node.getSubjectVar)
            val pred: Entity = makeEntityFromVar(node.getPredicateVar)
            val obj: Entity = makeEntityFromVar(node.getObjectVar)

            if (Option(node.getContextVar).nonEmpty) {
                throw GravsearchException("Named graphs are not supported in search queries")
            }

            wherePatterns.append(StatementPattern(subj = subj, pred = pred, obj = obj))
        }

        override def meet(node: algebra.Str): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Sum): Unit = {
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
                    case _: UnionPattern | _: OptionalPattern => throw GravsearchException(s"Nested blocks are not allowed in search queries, found $pattern")
                    case _ => ()
                }
            }

            patterns
        }

        override def meet(node: algebra.Union): Unit = {
            // Get the block of query patterns on the left side of the UNION.
            val leftPatterns: Seq[QueryPattern] = node.getLeftArg match {
                case _: algebra.Union => throw GravsearchException("Nested UNIONs are not allowed in search queries")

                case otherLeftArg =>
                    val leftArgVisitor = new ConstructQueryModelVisitor(isInNegation = isInNegation)
                    otherLeftArg.visit(leftArgVisitor)
                    positiveEntities ++= leftArgVisitor.positiveEntities
                    allIris ++= leftArgVisitor.allIris
                    checkBlockPatterns(leftArgVisitor.getWherePatterns)
            }

            // Get the block(s) of query patterns on the right side of the UNION.
            val rightPatterns: Seq[Seq[QueryPattern]] = node.getRightArg match {
                case rightArgUnion: algebra.Union =>
                    // If the right arg is also a UNION, recursively get its blocks. This represents a sequence of
                    // UNIONs rather than a nested UNION.
                    val rightArgVisitor = new ConstructQueryModelVisitor(isInNegation = isInNegation)
                    rightArgUnion.visit(rightArgVisitor)
                    positiveEntities ++= rightArgVisitor.positiveEntities
                    allIris ++= rightArgVisitor.allIris
                    val rightWherePatterns = rightArgVisitor.getWherePatterns

                    if (rightWherePatterns.size > 1) {
                        throw GravsearchException(s"Right argument of UNION is not a UnionPattern as expected: $rightWherePatterns")
                    }

                    rightWherePatterns.head match {
                        case rightUnionPattern: UnionPattern => rightUnionPattern.blocks
                        case other => throw GravsearchException(s"Right argument of UNION is not a UnionPattern as expected: $other")
                    }

                case otherRightArg =>
                    val rightArgVisitor = new ConstructQueryModelVisitor(isInNegation = isInNegation)
                    otherRightArg.visit(rightArgVisitor)
                    positiveEntities ++= rightArgVisitor.positiveEntities
                    allIris ++= rightArgVisitor.allIris
                    Seq(checkBlockPatterns(rightArgVisitor.getWherePatterns))
            }

            wherePatterns.append(UnionPattern(Seq(leftPatterns) ++ rightPatterns))
        }

        override def meet(node: algebra.ValueConstant): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.ListMemberOperator): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Var): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.ZeroLengthPath): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Regex): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Reduced): Unit = {
            node.visitChildren(this)
        }

        override def meet(node: algebra.ProjectionElemList): Unit = {
            // A ProjectionElemList represents the patterns in the CONSTRUCT clause. They're represented using
            // parser-generated constants instead of literal values, so for now we just have to store them that way
            // for now. Later, once we have the values of the constants, we will be able to build the CONSTRUCT clause.

            var subj: Option[String] = None
            var pred: Option[String] = None
            var obj: Option[String] = None

            for (projectionElem: algebra.ProjectionElem <- node.getElements.asScala) {
                val sourceName: String = projectionElem.getSourceName
                val targetName: String = projectionElem.getTargetName

                if (sourceName == targetName) {
                    throw GravsearchException(s"SELECT queries are not allowed in search, please use a CONSTRUCT query instead")
                }

                projectionElem.getTargetName match {
                    case "subject" => subj = Some(sourceName)
                    case "predicate" => pred = Some(sourceName)
                    case "object" => obj = Some(sourceName)
                    case _ => GravsearchException(s"SELECT queries are not allowed in search, please use a CONSTRUCT query instead")
                }
            }

            if (subj.isEmpty || pred.isEmpty || obj.isEmpty) {
                throw GravsearchException(s"Incomplete ProjectionElemList: $node")
            }

            constructStatementsWithConstants.append(ConstructStatementWithConstants(subj = subj.get, pred = pred.get, obj = obj.get))
        }

        override def meet(node: algebra.ProjectionElem): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Projection): Unit = {
            node.visitChildren(this)
        }

        override def meet(node: algebra.OrderElem): Unit = {
            // Ignored, because it's handled in meet(algebra.Order)
        }

        override def meet(node: algebra.Order): Unit = {
            for (orderElem: algebra.OrderElem <- node.getElements.asScala) {
                val expression: algebra.ValueExpr = orderElem.getExpr
                val ascending = orderElem.isAscending

                val queryVariable: QueryVariable = expression match {
                    case objVar: algebra.Var =>
                        makeEntityFromVar(objVar) match {
                            case queryVar: QueryVariable => queryVar
                            case _ => throw GravsearchException(s"Entity $objVar not allowed in ORDER BY")
                        }

                    case _ => throw GravsearchException(s"Expression $expression not allowed in ORDER BY")
                }

                orderBy.append(OrderCriterion(queryVariable = queryVariable, isAscending = ascending))
            }

            node.visitChildren(this)
        }

        override def meet(node: algebra.Or): Unit = {
            // Do nothing, because this is handled elsewhere.
        }

        override def meet(node: algebra.Not): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Namespace): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.MultiProjection): Unit = {
            node.visitChildren(this)
        }

        override def meet(move: algebra.Move): Unit = {
            unsupported(move)
        }

        override def meet(node: algebra.Coalesce): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Compare): Unit = {
            // Do nothing, because this is handled elsewhere.
        }

        override def meet(node: algebra.CompareAll): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.IsLiteral): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.IsNumeric): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.IsResource): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.IsURI): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.SameTerm): Unit = {
            unsupported(node)
        }

        override def meet(modify: algebra.Modify): Unit = {
            unsupported(modify)
        }

        override def meet(node: algebra.Min): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Max): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.ExtensionElem): Unit = {
            node.getExpr match {
                case valueConstant: algebra.ValueConstant =>
                    if (node.getName.startsWith("_const_")) {
                        // This is a parser-generated constant used in the CONSTRUCT clause. Just save it so we can
                        // build the CONSTRUCT clause correctly later.
                        valueConstants.put(node.getName, valueConstant)
                    } else {
                        // It's a BIND. Accept it if it refers to a Knora data IRI.
                        valueConstant.getValue match {
                            case iri: rdf4j.model.IRI =>
                                val variable = makeQueryVariable(node.getName)
                                val iriValue: IriRef = makeIri(iri)

                                if (!iriValue.iri.isKnoraDataIri) {
                                    throw GravsearchException(s"Unsupported IRI in BIND: ${iriValue.iri}")
                                }

                                val bindPattern = BindPattern(
                                    variable = variable,
                                    expression = iriValue
                                )

                                wherePatterns.append(bindPattern)

                            case other => throw GravsearchException(s"Unsupported value in BIND: $other")
                        }
                    }

                case _ => ()
            }
        }

        override def meet(node: algebra.Extension): Unit = {
            node.visitChildren(this)
        }

        override def meet(node: algebra.Exists): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.EmptySet): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Distinct): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Difference): Unit = {
            // Visit the nodes that the MINUS applies to.
            node.getLeftArg.visit(this)

            // Get the patterns inside the MINUS.
            val subQueryVisitor = new ConstructQueryModelVisitor(isInNegation = true)
            node.getRightArg.visit(subQueryVisitor)
            wherePatterns.append(MinusPattern(subQueryVisitor.getWherePatterns))
            allIris ++= subQueryVisitor.allIris
        }

        override def meet(deleteData: algebra.DeleteData): Unit = {
            unsupported(deleteData)
        }

        override def meet(node: algebra.Datatype): Unit = {
            unsupported(node)
        }

        override def meet(clear: algebra.Clear): Unit = {
            unsupported(clear)
        }

        override def meet(node: algebra.Bound): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.BNodeGenerator): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.BindingSetAssignment): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Avg): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.ArbitraryLengthPath): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.And): Unit = {
            // Do nothing, because this is handled elsewhere.
        }

        override def meet(add: algebra.Add): Unit = {
            unsupported(add)
        }

        override def meet(node: algebra.QueryRoot): Unit = {
            node.visitChildren(this)
        }

        override def meet(node: algebra.DescribeOperator): Unit = {
            throw GravsearchException(s"DESCRIBE queries are not allowed in search, please use a CONSTRUCT query instead")
        }

        override def meet(copy: algebra.Copy): Unit = {
            unsupported(copy)
        }

        override def meet(node: algebra.Count): Unit = {
            unsupported(node)
        }

        override def meet(create: algebra.Create): Unit = {
            unsupported(create)
        }

        override def meet(node: algebra.Sample): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Service): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.SingletonSet): Unit = {
            node.visitChildren(this)
        }

        override def meet(node: algebra.CompareAny): Unit = {
            unsupported(node)
        }

        private def makeFilterExpression(valueExpr: algebra.ValueExpr): Expression = {
            valueExpr match {
                case compare: algebra.Compare =>
                    val leftArg = makeFilterExpression(compare.getLeftArg)
                    val rightArg = makeFilterExpression(compare.getRightArg)
                    val operator = compare.getOperator.getSymbol

                    CompareExpression(
                        leftArg = leftArg,
                        operator = CompareExpressionOperator.lookup(operator, throw GravsearchException(s"Operator $operator is not supported in a CompareExpression")),
                        rightArg = rightArg
                    )

                case and: algebra.And =>
                    val leftArg = makeFilterExpression(and.getLeftArg)
                    val rightArg = makeFilterExpression(and.getRightArg)

                    AndExpression(
                        leftArg = leftArg,
                        rightArg = rightArg
                    )

                case or: algebra.Or =>
                    val leftArg = makeFilterExpression(or.getLeftArg)
                    val rightArg = makeFilterExpression(or.getRightArg)

                    OrExpression(
                        leftArg = leftArg,
                        rightArg = rightArg
                    )

                case valueConstant: algebra.ValueConstant => makeEntityFromValue(valueConstant.getValue)

                case sparqlVar: algebra.Var => makeEntityFromVar(sparqlVar)

                case functionCall: algebra.FunctionCall =>
                    val functionIri = IriRef(functionCall.getURI.toSmartIri)

                    val args: Seq[Entity] = functionCall.getArgs.asScala.map(arg => makeFilterExpression(arg)).map {
                        case entity: Entity => entity
                        case other => throw GravsearchException(s"Unsupported argument in function: $other")
                    }

                    FunctionCallExpression(
                        functionIri = functionIri,
                        args = args
                    )

                case regex: algebra.Regex =>
                    // first argument representing the text value to be checked
                    val textValueArg: algebra.ValueExpr = regex.getArg

                    val textValueVar: QueryVariable = textValueArg match {
                        case objVar: algebra.Var =>
                            makeEntityFromVar(objVar) match {
                                case queryVar: QueryVariable => queryVar
                                case _ => throw GravsearchException(s"Entity $objVar not allowed in regex function as the first argument, a variable is required")
                            }
                        case other => throw GravsearchException(s"$other is not allowed in regex function as first argument, a variable is required")
                    }

                    // second argument representing the REGEX pattern to be used to perform the check
                    val patternArg: algebra.ValueExpr = regex.getPatternArg

                    val pattern: String = patternArg match {
                        case valConstant: algebra.ValueConstant =>
                            valConstant.getValue.stringValue()
                        case other => throw GravsearchException(s"$other not allowed in regex function as the second argument, a string is expected")

                    }

                    // third argument representing the modifier to be used with when applying the REGEX pattern
                    val modifier: Option[String] = Option(regex.getFlagsArg) match {
                        case Some(valConstant: algebra.ValueConstant) => Some(valConstant.getValue.stringValue())
                        case None => None
                        case other => throw GravsearchException(s"$other not allowed in regex function as the third argument, a string is expected")

                    }

                    RegexFunction(
                        textVar = textValueVar,
                        pattern = pattern,
                        modifier = modifier
                    )

                case lang: algebra.Lang =>

                    val textValueVar = lang.getArg match {
                        case objVar: algebra.Var =>
                            makeEntityFromVar(objVar) match {
                                case queryVar: QueryVariable => queryVar
                                case _ => throw GravsearchException(s"Entity $objVar not allowed in lang function as an argument, a variable is required")
                            }
                        case other => throw GravsearchException(s"$other is not allowed in lang function as an argument, a variable is required")
                    }

                    LangFunction(textValueVar)

                case other => throw GravsearchException(s"Unsupported FILTER expression: ${other.getClass}")
            }
        }

        override def meet(node: algebra.Filter): Unit = {
            def makeFilterNotExists(not: algebra.Not): FilterNotExistsPattern = {
                not.getArg match {
                    case exists: algebra.Exists =>
                        val subQueryVisitor = new ConstructQueryModelVisitor(isInNegation = true)
                        exists.getSubQuery.visit(subQueryVisitor)
                        allIris ++= subQueryVisitor.allIris
                        FilterNotExistsPattern(subQueryVisitor.getWherePatterns)

                    case _ => throw GravsearchException(s"Unsupported NOT expression: $not")
                }
            }

            // Visit the nodes that the filter applies to.
            node.getArg.visit(this)

            // Is this a FILTER with an expression, or a FILTER NOT EXISTS?
            val filterQueryPattern: QueryPattern = node.getCondition match {
                case not: algebra.Not =>
                    // It's a FILTER NOT EXISTS.
                    makeFilterNotExists(not)

                case other =>
                    // It's a FILTER with an expression.
                    FilterPattern(expression = makeFilterExpression(other))
            }

            // Add the FILTER.
            wherePatterns.append(filterQueryPattern)
        }

        override def meet(node: algebra.FunctionCall): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Group): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.GroupConcat): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.GroupElem): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.If): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.In): Unit = {
            unsupported(node)
        }

        override def meet(insertData: algebra.InsertData): Unit = {
            unsupported(insertData)
        }

        override def meet(node: algebra.Intersection): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.IRIFunction): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.IsBNode): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.MathExpr): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.LocalName): Unit = {
            unsupported(node)
        }

        override def meet(load: algebra.Load): Unit = {
            unsupported(load)
        }

        override def meet(node: algebra.Like): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.LeftJoin): Unit = {
            // Visit the nodes that aren't in the OPTIONAL.
            node.getLeftArg.visit(this)

            // Visit the nodes that are in the OPTIONAL.
            val rightArgVisitor = new ConstructQueryModelVisitor(isInNegation = isInNegation)
            node.getRightArg.visit(rightArgVisitor)

            // If the OPTIONAL has a condition, construct a FILTER to represent the condition.
            val filterPattern = if (node.hasCondition) {
                Some(FilterPattern(makeFilterExpression(node.getCondition)))
            } else {
                None
            }

            positiveEntities ++= rightArgVisitor.positiveEntities
            allIris ++= rightArgVisitor.allIris
            wherePatterns.append(OptionalPattern(rightArgVisitor.getWherePatterns ++ filterPattern))
        }

        override def meet(node: algebra.LangMatches): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Lang): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Label): Unit = {
            unsupported(node)
        }

        override def meet(node: algebra.Join): Unit = {
            // Successive statements are connected by Joins.
            node.visitChildren(this)
        }

        override def meetOther(node: algebra.QueryModelNode): Unit = {
            unsupported(node)
        }
    }

}