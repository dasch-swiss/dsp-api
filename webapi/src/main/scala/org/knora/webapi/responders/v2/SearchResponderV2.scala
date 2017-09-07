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

package org.knora.webapi.responders.v2

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages.JulianDayNumberValueV1
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.search._
import org.knora.webapi.util.search.v2.{ApiV2Schema, _}
import org.knora.webapi.util.{ConstructResponseUtilV2, DateUtilV1, InputValidation}

import scala.collection.mutable
import scala.concurrent.Future

class SearchResponderV2 extends Responder {

    def receive = {
        case FulltextSearchGetRequestV2(searchValue, userProfile) => future2Message(sender(), fulltextSearchV2(searchValue, userProfile), log)
        case ExtendedSearchGetRequestV2(query, userProfile) => future2Message(sender(), extendedSearchV2(inputQuery = query, userProfile = userProfile), log)
        case SearchResourceByLabelRequestV2(searchValue, userProfile) => future2Message(sender(), searchResourcesByLabelV2(searchValue, userProfile), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Performs a fulltext search (simple search).
      *
      * @param searchValue the values to search for.
      * @param userProfile the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def fulltextSearchV2(searchValue: String, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        for {
            searchSparql <- Future(queries.sparql.v2.txt.searchFulltext(
                triplestore = settings.triplestoreType,
                searchTerms = searchValue
            ).toString())

            searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(searchSparql)).mapTo[SparqlConstructResponse]

            // separate resources and value objects
            queryResultsSeparated = ConstructResponseUtilV2.splitResourcesAndValueRdfData(constructQueryResults = searchResponse, userProfile = userProfile)

        } yield ReadResourcesSequenceV2(numberOfResources = queryResultsSeparated.size, resources = ConstructResponseUtilV2.createSearchResponse(queryResultsSeparated))

    }

    /**
      * Performs an extended search using a Sparql query provided by the user.
      *
      * @param inputQuery  Sparql construct query provided by the client.
      * @param userProfile the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def extendedSearchV2(inputQuery: ConstructQuery, apiSchema: ApiV2Schema.Value = ApiV2Schema.SIMPLE, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        if (apiSchema != ApiV2Schema.SIMPLE) {
            throw SparqlSearchException("Only api v2 simple is supported in v2 extended search")
        }

        /**
          * A [[ConstructToConstructTransformer]] that preprocesses the input CONSTRUCT query by converting external IRIs to internal ones
          * and disabling inference for individual statements as necessary.
          */
        class Preprocessor extends ConstructToConstructTransformer {
            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(preprocessStatementPattern(statementPattern = statementPattern))

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[QueryPattern] = Seq(preprocessStatementPattern(statementPattern = statementPattern))

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(FilterPattern(preprocessFilterExpression(filterPattern.expression)))

            /**
              * Preprocesses a [[Expression]] by converting external IRIs to internal ones.
              *
              * @param filterExpression a filter expression.
              * @return the preprocessed expression.
              */
            def preprocessFilterExpression(filterExpression: Expression): Expression = {
                filterExpression match {
                    case entity: Entity => preprocessEntity(entity)
                    case compareExpr: CompareExpression => CompareExpression(leftArg = preprocessFilterExpression(compareExpr.leftArg), operator = compareExpr.operator, rightArg = preprocessFilterExpression(compareExpr.rightArg))
                    case andExpr: AndExpression => AndExpression(leftArg = preprocessFilterExpression(andExpr.leftArg), rightArg = preprocessFilterExpression(andExpr.rightArg))
                    case orExpr: OrExpression => OrExpression(leftArg = preprocessFilterExpression(orExpr.leftArg), rightArg = preprocessFilterExpression(orExpr.rightArg))
                }
            }

            /**
              * Preprocesses an [[Entity]] by converting external IRIs to internal ones.
              *
              * @param entity an entity provided by [[SearchParserV2]].
              * @return the preprocessed entity.
              */
            def preprocessEntity(entity: Entity): Entity = {
                // convert external Iris to internal Iris if needed

                entity match {
                    case iriRef: IriRef => // if an Iri is an external knora-api entity (with value object or simple), convert it to an internal Iri
                        if (InputValidation.isKnoraApiEntityIri(iriRef.iri)) {
                            IriRef(InputValidation.externalIriToInternalIri(iriRef.iri, () => throw BadRequestException(s"${iriRef.iri} is not a valid external knora-api entity Iri")))
                        } else {
                            IriRef(InputValidation.toIri(iriRef.iri, () => throw BadRequestException(s"$iriRef is not a valid IRI")))
                        }

                    case other => other
                }
            }

            /**
              * Preprocesses a [[StatementPattern]] by converting external IRIs to internal ones and disabling inference if necessary.
              *
              * @param statementPattern a statement provided by SearchParserV2.
              * @return the preprocessed statement pattern.
              */
            def preprocessStatementPattern(statementPattern: StatementPattern): StatementPattern = {

                val subj = preprocessEntity(statementPattern.subj)
                val pred = preprocessEntity(statementPattern.pred)
                val obj = preprocessEntity(statementPattern.obj)

                StatementPattern.makeInferred(
                    subj = subj,
                    pred = pred,
                    obj = obj
                ) // use inference for all user-provided statements in Where clause
            }
        }

        /**
          * An abstract base class providing shared methods to query transformers.
          */
        abstract class AbstractTransformer extends WhereTransformer {

            val valueVariablesCreatedInFilters = mutable.Map.empty[QueryVariable, QueryVariable]

            /**
              * Convert an [[Entity]] to a [[TypeableEntity]] (key of type inspection results).
              * The entity is expected to be a variable or an Iri, otherwise `None` is returned.
              *
              * @param entity the entity to be converted to a [[TypeableEntity]].
              * @return an Option of a [[TypeableEntity]].
              */
            def toTypeableEntityKey(entity: Entity): Option[TypeableEntity] = {

                entity match {
                    case queryVar: QueryVariable => Some(TypeableVariable(queryVar.variableName))

                    case iriRef: IriRef =>
                        // type info keys are external api v2 simple Iris,
                        // so convert this internal Iri to an external api v2 simple if possible
                        val externalIri = if (InputValidation.isInternalEntityIri(iriRef.iri)) {
                            InputValidation.internalEntityIriToSimpleApiV2EntityIri(iriRef.iri, () => throw BadRequestException(s"${iriRef.iri} is not a valid internal knora-api entity Iri"))
                        } else {
                            iriRef.iri
                        }

                        Some(TypeableIri(externalIri))

                    case _ => None
                }

            }

            /**
              * Creates a syntactically valid variable base name, based on the given entity.
              *
              * @param entity the entity to be used to create a base name for a variable.
              * @return a base name for a variable.
              */
            def escapeEntityForVariable(entity: Entity): String = {
                val entityStr = entity match {
                    case QueryVariable(varName) => varName
                    case IriRef(iriLiteral) => iriLiteral
                    case XsdLiteral(stringLiteral, _) => stringLiteral
                    case _ => throw SparqlSearchException(s"A unique variable could not be made for $entity")
                }

                entityStr.replaceAll("[:/.#-]", "").replaceAll("\\s", "") // TODO: check if this is complete and if it could lea to collision of variable names
            }

            /**
              * Creates a unique variable name from the given entity and the local part of a property IRI.
              *
              * @param base        the entity to use to create the variable base name.
              * @param propertyIri the IRI of the property whose local part will be used to form the unique name.
              * @return a unique variable.
              */
            def createUniqueVariableNameFromEntityAndProperty(base: Entity, propertyIri: IRI): QueryVariable = {
                val propertyHashIndex = propertyIri.lastIndexOf('#')

                if (propertyHashIndex > 0) {
                    val propertyName = propertyIri.substring(propertyHashIndex + 1)
                    QueryVariable(escapeEntityForVariable(base) + "__" + escapeEntityForVariable(QueryVariable(propertyName)))
                } else {
                    throw AssertionException(s"Invalid property IRI: $propertyIri")
                }
            }

            /**
              * Create a unique variable from a whole statement.
              *
              * @param baseStatement the statement to be used to create the variable base name.
              * @param suffix        the suffix to be appended to the base name.
              * @return a unique variable.
              */
            def createUniqueVariableFromStatement(baseStatement: StatementPattern, suffix: String): QueryVariable = {
                QueryVariable(escapeEntityForVariable(baseStatement.subj) + "__" + escapeEntityForVariable(baseStatement.pred) + "__" + escapeEntityForVariable(baseStatement.obj) + "__" + suffix)
            }

            /**
              * Creates additional statements for a given [[Entity]] based on type information using `conversionFuncForNonPropertyType`
              * for a non property type (e.g., a resource).
              *
              * @param entity                           the entity to be taken into consideration (a statement's subject or object).
              * @param typeInspection                   type information.
              * @param processedTypeInfo                the keys of type information that have already been looked at.
              * @param conversionFuncForNonPropertyType the function to use to create additional statements.
              * @return a sequence of [[QueryPattern]] representing the additional statements.
              */
            def checkForNonPropertyTypeInfoForEntity(entity: Entity, typeInspection: TypeInspectionResult, processedTypeInfo: mutable.Set[TypeableEntity], conversionFuncForNonPropertyType: (NonPropertyTypeInfo, Entity) => Seq[QueryPattern]): Seq[QueryPattern] = {

                val typeInfoKey = toTypeableEntityKey(entity)

                if (typeInfoKey.nonEmpty && (typeInspection.typedEntities -- processedTypeInfo contains typeInfoKey.get)) {

                    val nonPropTypeInfo: NonPropertyTypeInfo = typeInspection.typedEntities(typeInfoKey.get) match {
                        case nonPropInfo: NonPropertyTypeInfo => nonPropInfo

                        case _ => throw AssertionException(s"NonPropertyTypeInfo expected for ${typeInfoKey.get}")
                    }

                    // add TypeableEntity (keys of `typeInspection`) for subject in order to prevent duplicates
                    processedTypeInfo += typeInfoKey.get

                    conversionFuncForNonPropertyType(
                        nonPropTypeInfo,
                        entity
                    )

                } else {
                    Seq.empty[QueryPattern]
                }

            }

            /**
              * Converts the given statement based on the given type information using `conversionFuncForPropertyType`.
              *
              * @param statementPattern              the statement to be converted.
              * @param typeInspection                type information.
              * @param conversionFuncForPropertyType the function to use for the conversion.
              * @return a sequence of [[QueryPattern]] representing the converted statement.
              */
            def checkForPropertyTypeInfoForStatement(statementPattern: StatementPattern, typeInspection: TypeInspectionResult, conversionFuncForPropertyType: (PropertyTypeInfo, StatementPattern) => Seq[QueryPattern]): Seq[QueryPattern] = {
                val predTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(statementPattern.pred)

                if (predTypeInfoKey.nonEmpty && (typeInspection.typedEntities contains predTypeInfoKey.get)) {
                    // process type information for the predicate into additional statements

                    val propTypeInfo = typeInspection.typedEntities(predTypeInfoKey.get) match {
                        case propInfo: PropertyTypeInfo => propInfo

                        case _ => throw AssertionException(s"PropertyTypeInfo expected for ${predTypeInfoKey.get}")
                    }

                    conversionFuncForPropertyType(
                        propTypeInfo,
                        statementPattern
                    )

                } else {
                    // no type information given and thus no further processing needed, just return the originally given statement (e.g., rdf:type)
                    Seq(statementPattern)
                }
            }

            // A Map of XSD types to the corresponding knora-base value predicates that point to literals.
            // This allows us to handle different types of values (value objects).
            val literalTypesToValueTypeIris: Map[IRI, IRI] = Map(
                OntologyConstants.Xsd.Integer -> OntologyConstants.KnoraBase.ValueHasInteger,
                OntologyConstants.Xsd.Decimal -> OntologyConstants.KnoraBase.ValueHasDecimal,
                OntologyConstants.Xsd.Boolean -> OntologyConstants.KnoraBase.ValueHasBoolean,
                OntologyConstants.Xsd.String -> OntologyConstants.KnoraBase.ValueHasString,
                OntologyConstants.KnoraBase.Date -> OntologyConstants.KnoraBase.ValueHasStartJDN
            )

            case class TransformedFilterExpression(expression: Expression, additionalStatements: Seq[StatementPattern] = Seq.empty[StatementPattern])

            def transformFilterExpression(filterExpression: Expression, typeInspection: TypeInspectionResult): TransformedFilterExpression = {

                filterExpression match {

                    case filterCompare: CompareExpression =>

                        // left argument of a CompareExpression is expected to be a QueryVariable

                        val queryVar: QueryVariable = filterCompare.leftArg match {

                            case queryVar: QueryVariable => queryVar

                            case other => throw SparqlSearchException(s"Left argument of a Filter CompareExpression is expected to be a QueryVariable, but $other is given")
                        }

                        val queryVarTypeInfoKey = toTypeableEntityKey(queryVar)

                        // get information about the queryVar's type
                        if (queryVarTypeInfoKey.nonEmpty && (typeInspection.typedEntities contains queryVarTypeInfoKey.get)) {

                            val typeInfo: SparqlEntityTypeInfo = typeInspection.typedEntities(queryVarTypeInfoKey.get)

                            // check if type information is about a property or a value
                            typeInfo match {

                                case propInfo: PropertyTypeInfo =>
                                    // the left arg (a variable) represents a property

                                    // get the internal objectIri of the property type info
                                    val typeIriInternal = if (InputValidation.isKnoraApiEntityIri(propInfo.objectTypeIri)) {
                                        InputValidation.externalIriToInternalIri(propInfo.objectTypeIri, () => throw BadRequestException(s"${propInfo.objectTypeIri} is not a valid external knora-api entity Iri"))
                                    } else {
                                        propInfo.objectTypeIri
                                    }

                                    typeIriInternal match {

                                        case OntologyConstants.KnoraBase.Resource =>
                                            // left arg is variable representing a property pointing to a knora-base:Resource, so the right argument must be an Iri
                                            filterCompare.rightArg match {
                                                case iriRef: IriRef =>

                                                    // make sure that the comparison operator is a `CompareExpressionOperator.EQUALS`
                                                    if (filterCompare.operator != CompareExpressionOperator.EQUALS) throw SparqlSearchException(s"Comparison operator in a CompareExpression for a property type is expected to be ${CompareExpressionOperator.EQUALS}, but ${filterCompare.operator} given. For negations use 'FILTER NOT EXISTS' ")

                                                    TransformedFilterExpression(CompareExpression(filterCompare.leftArg, filterCompare.operator, filterCompare.rightArg))

                                                case other => throw SparqlSearchException(s"right argument of CompareExpression is expected to be an Iri representing a property, but $other is given")
                                            }

                                    }

                                case nonPropInfo: NonPropertyTypeInfo =>
                                    // the left arg (a variable) represents a value

                                    // get the internal type Iri of the non property type info
                                    val typeIriInternal = if (InputValidation.isKnoraApiEntityIri(nonPropInfo.typeIri)) {
                                        InputValidation.externalIriToInternalIri(nonPropInfo.typeIri, () => throw BadRequestException(s"${nonPropInfo.typeIri} is not a valid external knora-api entity Iri"))
                                    } else {
                                        nonPropInfo.typeIri
                                    }

                                    typeIriInternal match {

                                        case OntologyConstants.Xsd.Integer =>

                                            // make sure that the right argument is an integer literal
                                            val integerLiteral: XsdLiteral = filterCompare.rightArg match {
                                                case intLiteral: XsdLiteral if intLiteral.datatype == OntologyConstants.Xsd.Integer => intLiteral

                                                case other => throw SparqlSearchException(s"right argument in CompareExpression for integer property was expected to be an integer literal, but $other is given.")
                                            }

                                            val intValHasInteger: QueryVariable = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasInteger)

                                            valueVariablesCreatedInFilters.put(queryVar, intValHasInteger)

                                            TransformedFilterExpression(
                                                CompareExpression(intValHasInteger, filterCompare.operator, integerLiteral),
                                                Seq(
                                                    StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasInteger), intValHasInteger)
                                                )
                                            )

                                        case OntologyConstants.Xsd.Decimal =>

                                            // make sure that the right argument is a decimal literal
                                            val decimalLiteral: XsdLiteral = filterCompare.rightArg match {
                                                case decimalLiteral: XsdLiteral if decimalLiteral.datatype == OntologyConstants.Xsd.Decimal || decimalLiteral.datatype == OntologyConstants.Xsd.Integer => decimalLiteral

                                                case other => throw SparqlSearchException(s"right argument in CompareExpression for decimal property was expected to be a decimal or an integer literal, but $other is given.")
                                            }

                                            val decimalValHasDecimal = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasDecimal)

                                            valueVariablesCreatedInFilters.put(queryVar, decimalValHasDecimal)

                                            TransformedFilterExpression(
                                                CompareExpression(decimalValHasDecimal, filterCompare.operator, decimalLiteral),
                                                Seq(
                                                    StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasDecimal), decimalValHasDecimal)
                                                )
                                            )

                                        case OntologyConstants.Xsd.Boolean =>

                                            // make sure that the right argument is an integer literal
                                            val booleanLiteral: XsdLiteral = filterCompare.rightArg match {
                                                case booleanLiteral: XsdLiteral if booleanLiteral.datatype == OntologyConstants.Xsd.Boolean => booleanLiteral

                                                case other => throw SparqlSearchException(s"right argument in CompareExpression for boolean property was expected to be a boolean literal, but $other is given.")
                                            }

                                            val booleanValHasBoolean = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasBoolean)

                                            valueVariablesCreatedInFilters.put(queryVar, booleanValHasBoolean)

                                            // check if operator is supported for string operations
                                            if (!(filterCompare.operator.equals(CompareExpressionOperator.EQUALS) || filterCompare.operator.equals(CompareExpressionOperator.NOT_EQUALS))) {
                                                throw SparqlSearchException(s"Filter expressions for a boolean value supports the following operators: ${CompareExpressionOperator.EQUALS}, ${CompareExpressionOperator.NOT_EQUALS}, but ${filterCompare.operator} given")
                                            }

                                            TransformedFilterExpression(
                                                CompareExpression(booleanValHasBoolean, filterCompare.operator, booleanLiteral),
                                                Seq(
                                                    StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasBoolean), booleanValHasBoolean)
                                                )
                                            )

                                        case OntologyConstants.Xsd.String =>

                                            // make sure that the right argument is a string literal
                                            val stringLiteral: XsdLiteral = filterCompare.rightArg match {
                                                case strLiteral: XsdLiteral if strLiteral.datatype == OntologyConstants.Xsd.String => strLiteral

                                                case other => throw SparqlSearchException(s"right argument in CompareExpression for string property was expected to be a string literal, but $other is given.")
                                            }

                                            val textValHasString = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasString)

                                            valueVariablesCreatedInFilters.put(queryVar, textValHasString)

                                            // check if operator is supported for string operations
                                            if (!(filterCompare.operator.equals(CompareExpressionOperator.EQUALS) || filterCompare.operator.equals(CompareExpressionOperator.NOT_EQUALS))) {
                                                throw SparqlSearchException(s"Filter expressions for a string value supports the following operators: ${CompareExpressionOperator.EQUALS}, ${CompareExpressionOperator.NOT_EQUALS}, but ${filterCompare.operator} given")
                                            }

                                            TransformedFilterExpression(
                                                CompareExpression(textValHasString, filterCompare.operator, stringLiteral),
                                                Seq(
                                                    StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasString), textValHasString)
                                                )
                                            )

                                        case OntologyConstants.KnoraBase.Date =>

                                            // make sure that the right argument is a string literal
                                            val dateStringLiteral: _root_.org.knora.webapi.util.search.XsdLiteral = filterCompare.rightArg match {
                                                case dateStrLiteral: XsdLiteral if dateStrLiteral.datatype == OntologyConstants.Xsd.String => dateStrLiteral

                                                case other => throw SparqlSearchException(s"right argument in CompareExpression for date property was expected to be a string literal representing a date, but $other is given.")
                                            }

                                            val dateStr = InputValidation.toDate(dateStringLiteral.value, () => throw BadRequestException(s"${dateStringLiteral.value} is not a valid date string"))

                                            val date: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(dateStr)

                                            val dateValueHasStartVar = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasStartJDN)

                                            // sort dates by their period's start
                                            valueVariablesCreatedInFilters.put(queryVar, dateValueHasStartVar)

                                            val dateValueHasEndVar = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasEndJDN)

                                            val dateValStartStatement = StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStartJDN), obj = dateValueHasStartVar)

                                            val dateValEndStatement = StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasEndJDN), obj = dateValueHasEndVar)

                                            // process filter expression based on given comparison operator
                                            filterCompare.operator match {

                                                case CompareExpressionOperator.EQUALS =>

                                                    // any overlap in considered as equality
                                                    val leftArgFilter = CompareExpression(XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer), CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO, dateValueHasEndVar)

                                                    val rightArgFilter = CompareExpression(XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer), CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO, dateValueHasStartVar)

                                                    val filter = AndExpression(leftArgFilter, rightArgFilter)

                                                    TransformedFilterExpression(
                                                        filter,
                                                        Seq(
                                                            dateValStartStatement, dateValEndStatement
                                                        )
                                                    )

                                                case CompareExpressionOperator.NOT_EQUALS =>

                                                    // no overlap in considered as inequality
                                                    val leftArgFilter = CompareExpression(XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer), CompareExpressionOperator.GREATER_THAN, dateValueHasEndVar)

                                                    val rightArgFilter = CompareExpression(XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer), CompareExpressionOperator.LESS_THAN, dateValueHasStartVar)

                                                    val filter = OrExpression(leftArgFilter, rightArgFilter)

                                                    TransformedFilterExpression(
                                                        filter,
                                                        Seq(
                                                            dateValStartStatement, dateValEndStatement
                                                        )
                                                    )

                                                case CompareExpressionOperator.LESS_THAN =>

                                                    // period ends before indicated period
                                                    val filter = CompareExpression(dateValueHasEndVar, CompareExpressionOperator.LESS_THAN, XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer))

                                                    TransformedFilterExpression(
                                                        filter,
                                                        Seq(dateValEndStatement)
                                                    )

                                                case CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO =>

                                                    // period ends before indicated period or equals it (any overlap)
                                                    val filter = CompareExpression(dateValueHasStartVar, CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO, XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer))

                                                    TransformedFilterExpression(
                                                        filter,
                                                        Seq(dateValStartStatement)
                                                    )

                                                case CompareExpressionOperator.GREATER_THAN =>

                                                    // period starts after end of indicated period
                                                    val filter = CompareExpression(dateValueHasStartVar, CompareExpressionOperator.GREATER_THAN, XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer))

                                                    TransformedFilterExpression(
                                                        filter,
                                                        Seq(dateValStartStatement)
                                                    )

                                                case CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO =>

                                                    // period starts after indicated period or equals it (any overlap)
                                                    val filter = CompareExpression(dateValueHasEndVar, CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO, XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer))

                                                    TransformedFilterExpression(
                                                        filter,
                                                        Seq(dateValEndStatement)
                                                    )


                                                case other => throw SparqlSearchException(s"operator $other not supported in filter expressions for dates")


                                            }

                                        case other => throw NotImplementedException(s"$other not supported in FilterExpression")


                                    }


                            }


                        } else {
                            throw SparqlSearchException(s"type information about $queryVar is missing")
                        }


                    case filterOr: OrExpression =>
                        val filterExpressionLeft = transformFilterExpression(filterOr.leftArg, typeInspection)
                        val filterExpressionRight = transformFilterExpression(filterOr.rightArg, typeInspection)

                        TransformedFilterExpression(
                            OrExpression(filterExpressionLeft.expression, filterExpressionRight.expression),
                            filterExpressionLeft.additionalStatements ++ filterExpressionRight.additionalStatements
                        )


                    case filterAnd: AndExpression =>
                        val filterExpressionLeft = transformFilterExpression(filterAnd.leftArg, typeInspection)
                        val filterExpressionRight = transformFilterExpression(filterAnd.rightArg, typeInspection)

                        TransformedFilterExpression(
                            AndExpression(filterExpressionLeft.expression, filterExpressionRight.expression),
                            filterExpressionLeft.additionalStatements ++ filterExpressionRight.additionalStatements
                        )

                    case other => throw NotImplementedException(s"$other not supported as FilterExpression")
                }

            }

        }

        /**
          * Checks if a statement represents the knora-base:isMainResource statement and returns the query variable representing the main resource if so.
          *
          * @param statementPattern the statement pattern to be checked.
          * @return query variable representing the main resource or None.
          */
        def isMainResourceVariable(statementPattern: StatementPattern): Option[QueryVariable] = {
            statementPattern.pred match {
                case IriRef(OntologyConstants.KnoraBase.IsMainResource) =>
                    statementPattern.obj match {
                        case XsdLiteral("true", OntologyConstants.Xsd.Boolean) =>
                            statementPattern.subj match {
                                case queryVariable: QueryVariable => Some(queryVariable)
                                case _ => throw SparqlSearchException(s"The subject of ${OntologyConstants.KnoraBase.IsMainResource} must be a variable") // TODO: use the knora-api predicate in the error message?
                            }

                        case _ => None
                    }

                case _ => None
            }
        }

        /**
          * Transforms a preprocessed CONSTRUCT query into a SELECT query that returns only the IRIs and sort order of the main resources that matched
          * the search criteria. This query will be used to get resource IRIs for a single page of results. These IRIs will be included in a CONSTRUCT
          * query to get the actual results for the page.
          *
          * @param typeInspectionResult the result of type inspection of the original query.
          */
        class NonTriplestoreSpecificConstructToSelectTransformer(typeInspectionResult: TypeInspectionResult) extends AbstractTransformer with ConstructToSelectTransformer {

            // a Set containing all `TypeableEntity` (keys of `typeInspectionResult`) that have already been processed
            // in order to prevent duplicates
            private val processedTypeInformationKeysWhereClause = mutable.Set.empty[TypeableEntity]

            private var mainResourceVariable: Option[QueryVariable] = None

            /**
              * Creates additional statements for a non property type (e.g., a resource).
              *
              * @param nonPropertyTypeInfo type information about non property type.
              * @param inputEntity         the [[Entity]] to make the statements about.
              * @return a sequence of [[QueryPattern]] representing the additional statements.
              */
            def createAdditionalStatementsForNonPropertyType(nonPropertyTypeInfo: NonPropertyTypeInfo, inputEntity: Entity): Seq[QueryPattern] = {

                val typeIriInternal = if (InputValidation.isKnoraApiEntityIri(nonPropertyTypeInfo.typeIri)) {
                    InputValidation.externalIriToInternalIri(nonPropertyTypeInfo.typeIri, () => throw BadRequestException(s"${nonPropertyTypeInfo.typeIri} is not a valid external knora-api entity Iri"))
                } else {
                    nonPropertyTypeInfo.typeIri
                }

                if (typeIriInternal == OntologyConstants.KnoraBase.Resource) {

                    // inputEntity is either source or target of a linking property
                    // create additional statements in order to query permissions and other information for a resource

                    Seq(
                        StatementPattern.makeInferred(subj = inputEntity, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.Resource)),
                        StatementPattern.makeExplicit(subj = inputEntity, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean))
                    )
                } else {
                    // inputEntity is target of a value property
                    // properties are handled by `convertStatementForPropertyType`, no processing needed here

                    Seq.empty[QueryPattern]
                }
            }

            def convertStatementForPropertyType(propertyTypeInfo: PropertyTypeInfo, statementPattern: StatementPattern): Seq[QueryPattern] = {
                // decide whether to keep the originally given statement or not
                // if pred is a valueProp and the simple api is used, do not return the original statement
                // it had to be converted to comply with Knora's value object structure

                // convert the type information into an internal Knora Iri if possible
                val objectIri = if (InputValidation.isKnoraApiEntityIri(propertyTypeInfo.objectTypeIri)) {
                    InputValidation.externalIriToInternalIri(propertyTypeInfo.objectTypeIri, () => throw BadRequestException(s"${propertyTypeInfo.objectTypeIri} is not a valid external knora-api entity Iri"))
                } else {
                    propertyTypeInfo.objectTypeIri
                }

                objectIri match {
                    case OntologyConstants.KnoraBase.Resource => {

                        // make sure that the object is either an Iri or a variable (cannot be a literal)
                        statementPattern.obj match {
                            case iriRef: IriRef => ()
                            case queryVar: QueryVariable => ()
                            case other => throw SparqlSearchException(s"Object of a linking statement must be an Iri or a QueryVariable, but $other given.")
                        }

                        // linking property: just include the original statement relating the subject to the target of the link
                        Seq(statementPattern)
                    }

                    case literalType: IRI => {
                        // value property

                        // make sure that the object is a query variable (literals are not supported yet)
                        statementPattern.obj match {
                            case queryVar: QueryVariable => ()
                            case other => throw SparqlSearchException(s"Object of a value property statement must be a QueryVariable, but $other given.")
                        }

                        // check that value object is not marked as deleted
                        val valueObjectIsNotDeleted = StatementPattern.makeExplicit(subj = statementPattern.obj, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean))

                        // the query variable stands for a value object
                        // if there is a filter statement, the literal of the value object has to be checked: e.g., valueHasInteger etc.
                        // include the original statement relating the subject to a value object
                        Seq(statementPattern, valueObjectIsNotDeleted)
                    }
                }

            }

            private def processStatementPatternFromWhereClause(statementPattern: StatementPattern): Seq[QueryPattern] = {

                // look at the statement's subject, predicate, and object and generate additional statements if needed based on the given type information.
                // transform the originally given statement if necessary when processing the predicate

                // check if there exists type information for the given statement's subject
                val additionalStatementsForSubj: Seq[QueryPattern] = checkForNonPropertyTypeInfoForEntity(statementPattern.subj, typeInspectionResult, processedTypeInformationKeysWhereClause, createAdditionalStatementsForNonPropertyType)

                // check if there exists type information for the given statement's object
                val additionalStatementsForObj: Seq[QueryPattern] = checkForNonPropertyTypeInfoForEntity(statementPattern.obj, typeInspectionResult, processedTypeInformationKeysWhereClause, createAdditionalStatementsForNonPropertyType)

                // Add additional statements based on the whole input statement, e.g. to deal with the value object or the link value, and transform the original statement.
                val additionalStatementsForWholeStatement: Seq[QueryPattern] = checkForPropertyTypeInfoForStatement(statementPattern, typeInspectionResult, convertStatementForPropertyType)

                additionalStatementsForSubj ++ additionalStatementsForWholeStatement ++ additionalStatementsForObj

            }

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
              * @return the result of the transformation.
              */
            override def transformStatementInWhere(statementPattern: StatementPattern): Seq[QueryPattern] = {
                // Include any statements needed to meet the user's search criteria, but not statements that would be needed for permission checking or
                // other information about the matching resources or values.

                processStatementPatternFromWhereClause(
                    statementPattern = statementPattern
                )
            }

            /**
              * Transforms a [[FilterPattern]] in a WHERE clause into zero or more statement patterns.
              *
              * @param filterPattern the filter to be transformed.
              * @return the result of the transformation.
              */
            override def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = {

                val filterExpression: TransformedFilterExpression = transformFilterExpression(filterPattern.expression, typeInspection = typeInspectionResult)

                filterExpression.additionalStatements :+ FilterPattern(filterExpression.expression)

            }

            /**
              * Returns the variables that should be included in the results of the SELECT query. This method will be called
              * by [[QueryTraverser]] after the whole input query has been traversed.
              *
              * @return the variables that should be returned by the SELECT.
              */
            override def getSelectVariables: Seq[QueryVariable] = {
                // Return the main resource variable and the generated variable that we're using for ordering.

                mainResourceVariable match {
                    case Some(mainVar: QueryVariable) => Seq(mainVar) // TODO: append ORDER BY vars

                    case None => throw SparqlSearchException(s"No ${OntologyConstants.KnoraBase.IsMainResource} found in CONSTRUCT query.")
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

                        valueVariablesCreatedInFilters.get(criterion.queryVariable) match {
                            case Some(generatedVariable) =>
                                // Yes. Use the already generated variable in the ORDER BY.
                                acc.copy(
                                    orderBy = acc.orderBy :+ OrderCriterion(queryVariable = generatedVariable, isAscending = criterion.isAscending)
                                )

                            case None =>
                                // No. Generate such a variable and generate an additional statement to get its literal value in the WHERE clause.

                                // What is the type of the literal value?
                                val typeableEntity = TypeableVariable(criterion.queryVariable.variableName)
                                val typeInfo: SparqlEntityTypeInfo = typeInspectionResult.typedEntities.getOrElse(typeableEntity, throw SparqlSearchException(s"No type information found for ${criterion.queryVariable}"))

                                // Get the corresponding knora-base:valueHas* property so we can generate an appropriate variable name.
                                val propertyIri: IRI = typeInfo match {
                                    case nonPropertyTypeInfo: NonPropertyTypeInfo =>
                                        literalTypesToValueTypeIris.getOrElse(nonPropertyTypeInfo.typeIri, throw SparqlSearchException(s"Type ${nonPropertyTypeInfo.typeIri} is not supported in ORDER BY"))

                                    case _: PropertyTypeInfo => throw SparqlSearchException(s"Variable ${criterion.queryVariable.variableName} represents a property, and therefore cannot be used in ORDER BY")
                                }

                                // Generate the variable name.
                                val variableForLiteral: QueryVariable = createUniqueVariableNameFromEntityAndProperty(criterion.queryVariable, propertyIri)

                                // Generate a statement to get the literal value.
                                val statementPattern = StatementPattern.makeExplicit(subj = criterion.queryVariable, pred = IriRef(propertyIri), obj = variableForLiteral)

                                acc.copy(
                                    statementPatterns = acc.statementPatterns :+ statementPattern,
                                    orderBy = acc.orderBy :+ OrderCriterion(queryVariable = variableForLiteral, isAscending = criterion.isAscending)
                                )
                        }
                }

                // always order by include main resource (Order By criterion is optional)
                transformedOrderBy.copy(
                    orderBy = transformedOrderBy.orderBy :+ OrderCriterion(
                        queryVariable = mainResourceVariable.getOrElse(throw SparqlSearchException("\"No ${OntologyConstants.KnoraBase.IsMainResource} found in CONSTRUCT query.")),
                        isAscending = true
                    )
                )
            }
        }

        /**
          * A [[ConstructToConstructTransformer]] that generates non-triplestore-specific SPARQL.
          *
          * @param typeInspectionResult the result of type inspection of the original query.
          */
        class NonTriplestoreSpecificConstructToConstructTransformer(typeInspectionResult: TypeInspectionResult) extends AbstractTransformer with ConstructToConstructTransformer {

            // a Set containing all `TypeableEntity` (keys of `typeInspectionResult`) that have already been processed
            // in order to prevent duplicates
            private val processedTypeInformationKeysWhereClause = mutable.Set.empty[TypeableEntity]

            // a Map containing all additional StatementPattern that have been created based on the type information, FilterPattern excluded
            // will be integrated in the Construct clause
            private val additionalStatementsCreatedForEntities = mutable.Map.empty[Entity, Seq[StatementPattern]]

            // a Map containing all the statements of the Where clause (possibly converted) and additional statements created for them
            // will be integrated in the Construct clause
            private val convertedStatementsCreatedForWholeStatements = mutable.Map.empty[StatementPattern, Seq[StatementPattern]]

            def createAdditionalStatementsForNonPropertyType(nonPropertyTypeInfo: NonPropertyTypeInfo, inputEntity: Entity): Seq[QueryPattern] = {

                val typeIriInternal = if (InputValidation.isKnoraApiEntityIri(nonPropertyTypeInfo.typeIri)) {
                    InputValidation.externalIriToInternalIri(nonPropertyTypeInfo.typeIri, () => throw BadRequestException(s"${nonPropertyTypeInfo.typeIri} is not a valid external knora-api entity Iri"))
                } else {
                    nonPropertyTypeInfo.typeIri
                }

                if (typeIriInternal == OntologyConstants.KnoraBase.Resource) {

                    // inputEntity is either source or target of a linking property
                    // create additional statements in order to query permissions and other information for a resource

                    val resourcePropVar = createUniqueVariableNameFromEntityAndProperty(inputEntity, OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "ResProp")
                    val resourcePropObjVar = createUniqueVariableNameFromEntityAndProperty(inputEntity, OntologyConstants.KnoraBase.KnoraBasePrefixExpansion + "ResObj")

                    val addedStatementsForResource = Seq(
                        StatementPattern.makeInferred(subj = inputEntity, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.Resource)),
                        StatementPattern.makeExplicit(subj = inputEntity, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)),
                        StatementPattern.makeExplicit(subj = inputEntity, pred = resourcePropVar, obj = resourcePropObjVar)
                    )

                    val filterNotExists = Seq(FilterNotExistsPattern(
                        patterns = Seq(StatementPattern.makeInferred(subj = inputEntity, pred = IriRef(OntologyConstants.KnoraBase.ResourceProperty), obj = resourcePropObjVar))
                    ))

                    // TODO: only query a resource's values if properties are requested for this resource because this makes the query slow



                    val valueObjectVar = createUniqueVariableNameFromEntityAndProperty(inputEntity, OntologyConstants.KnoraBase.Value)
                    val valuePropVar = createUniqueVariableNameFromEntityAndProperty(inputEntity, OntologyConstants.KnoraBase.HasValue)
                    val valueObjectType = createUniqueVariableNameFromEntityAndProperty(valueObjectVar, OntologyConstants.Rdf.Type)
                    val valueObjectProp = createUniqueVariableNameFromEntityAndProperty(valueObjectVar, OntologyConstants.KnoraBase.HasValue)
                    val valueObjectValue = createUniqueVariableNameFromEntityAndProperty(valueObjectVar, OntologyConstants.KnoraBase.Value)

                    val addedStatementsForValues = Seq(StatementPattern.makeInferred(subj = inputEntity, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = valueObjectVar),
                        StatementPattern.makeExplicit(subj = inputEntity, pred = valuePropVar, obj = valueObjectVar),
                        StatementPattern.makeExplicit(subj = valueObjectVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)),
                        StatementPattern.makeExplicit(subj = valueObjectVar, pred = valueObjectProp, obj = valueObjectValue)
                    )

                    // Add statements to `additionalStatementsCreatedForEntities` since they are needed in the query's CONSTRUCT clause
                    val existingAdditionalStatementsCreated: Seq[StatementPattern] = additionalStatementsCreatedForEntities.get(inputEntity).toSeq.flatten

                    additionalStatementsCreatedForEntities += inputEntity -> (existingAdditionalStatementsCreated ++ addedStatementsForResource ++ addedStatementsForValues)

                    Seq(UnionPattern(blocks = Seq(addedStatementsForResource ++ filterNotExists, addedStatementsForValues)))

                    //addedStatementsForResource ++ filterNotExists

                } else {
                    // inputEntity is target of a value property
                    // properties are handled by `convertStatementForPropertyType`, no processing needed here

                    Seq.empty[QueryPattern]
                }


            }

            def convertStatementForPropertyType(propertyTypeInfo: PropertyTypeInfo, statementPattern: StatementPattern): Seq[QueryPattern] = {

                // Add statements to `convertedStatementsCreatedForWholeStatements` if they are needed in the query's CONSTRUCT clause

                // decide whether to keep the originally given statement or not
                // if pred is a valueProp and the simple api is used, do not return the original statement
                // it had to be converted to comply with Knora's value object structure

                // convert the type information into an internal Knora Iri if possible
                val objectIri = if (InputValidation.isKnoraApiEntityIri(propertyTypeInfo.objectTypeIri)) {
                    InputValidation.externalIriToInternalIri(propertyTypeInfo.objectTypeIri, () => throw BadRequestException(s"${propertyTypeInfo.objectTypeIri} is not a valid external knora-api entity Iri"))
                } else {
                    propertyTypeInfo.objectTypeIri
                }

                objectIri match {
                    case OntologyConstants.KnoraBase.Resource => {

                        // make sure that the object is either an Iri or a variable (cannot be a literal)
                        statementPattern.obj match {
                            case iriRef: IriRef => ()
                            case queryVar: QueryVariable => ()
                            case other => throw SparqlSearchException(s"Object of a linking statement must be an Iri or a QueryVariable, but $other given.")
                        }

                        val existingConvertedStatements: Seq[StatementPattern] = convertedStatementsCreatedForWholeStatements.get(statementPattern).toSeq.flatten

                        // include the original statement relating the subject to the target in the query's CONSTRUCT clause
                        convertedStatementsCreatedForWholeStatements += statementPattern -> (existingConvertedStatements :+ statementPattern)

                        // linking property: just include the original statement relating the subject to the target of the link
                        Seq(statementPattern)
                    }

                    case literalType: IRI => {
                        // value property

                        // make sure that the object is a query variable (literals are not supported yet)
                        statementPattern.obj match {
                            case queryVar: QueryVariable => ()
                            case other => throw SparqlSearchException(s"Object of a value property statement must be a QueryVariable, but $other given.")
                        }

                        val existingConvertedStatements: Seq[StatementPattern] = convertedStatementsCreatedForWholeStatements.get(statementPattern).toSeq.flatten

                        // do not include the original statement relating the subject to a value object in the query's CONSTRUCT clause
                        convertedStatementsCreatedForWholeStatements += statementPattern -> (existingConvertedStatements ++ Seq.empty[StatementPattern])

                        // check that value object is not marked as deleted
                        val valueObjectIsNotDeleted = StatementPattern.makeExplicit(subj = statementPattern.obj, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean))

                        // the query variable stands for a value object
                        // if there is a filter statement, the literal of the value object has to be checked: e.g., valueHasInteger etc.
                        // include the original statement relating the subject to a value object
                        Seq(statementPattern, valueObjectIsNotDeleted)
                    }
                }

            }

            /**
              * Process a given statement pattern based on type information.
              * This function is used for the Construct and Where clause of a user provided Sparql query.
              *
              * @param statementPattern the statement to be processed.
              * @return a sequence of [[StatementPattern]].
              */
            def processStatementPatternFromWhereClause(statementPattern: StatementPattern): Seq[QueryPattern] = {
                // look at the statement's subject, predicate, and object and generate additional statements if needed based on the given type information.
                // transform the originally given statement if necessary when processing the predicate

                // check if there exists type information for the given statement's subject
                val additionalStatementsForSubj: Seq[QueryPattern] = checkForNonPropertyTypeInfoForEntity(statementPattern.subj, typeInspectionResult, processedTypeInformationKeysWhereClause, createAdditionalStatementsForNonPropertyType)

                // check if there exists type information for the given statement's object
                val additionalStatementsForObj: Seq[QueryPattern] = checkForNonPropertyTypeInfoForEntity(statementPattern.obj, typeInspectionResult, processedTypeInformationKeysWhereClause, createAdditionalStatementsForNonPropertyType)

                // Add additional statements based on the whole input statement, e.g. to deal with the value object or the link value, and transform the original statement.
                val additionalStatementsForWholeStatement: Seq[QueryPattern] = checkForPropertyTypeInfoForStatement(statementPattern, typeInspectionResult, convertStatementForPropertyType)

                additionalStatementsForSubj ++ additionalStatementsForWholeStatement ++ additionalStatementsForObj

            }

            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = {

                // check if there is an entry for the given statementPattern in additionalStatementsCreated

                val additionalStatementsForSubj = additionalStatementsCreatedForEntities.get(statementPattern.subj) match {
                    case Some(statementPatterns: Seq[StatementPattern]) =>
                        additionalStatementsCreatedForEntities -= statementPattern.subj

                        statementPatterns.map {
                            // set graph to None for Construct clause
                            additionalStatementP: StatementPattern => additionalStatementP.copy(namedGraph = None)
                        }

                    case None => Seq.empty[StatementPattern]
                }

                val additionalStatementsForObj = additionalStatementsCreatedForEntities.get(statementPattern.obj) match {
                    case Some(statementPatterns: Seq[StatementPattern]) =>
                        additionalStatementsCreatedForEntities -= statementPattern.obj

                        statementPatterns.map {
                            // set graph to None for Construct clause
                            additionalStatementP: StatementPattern => additionalStatementP.copy(namedGraph = None)
                        }

                    case None => Seq.empty[StatementPattern]
                }

                val convertedStatementsForWholeStatement = convertedStatementsCreatedForWholeStatements.get(statementPattern) match {
                    case Some(statementPatterns: Seq[StatementPattern]) =>
                        convertedStatementsCreatedForWholeStatements -= statementPattern

                        statementPatterns.map {
                            // set graph to None for Construct clause
                            additionalStatementP: StatementPattern => additionalStatementP.copy(namedGraph = None)
                        }

                    case None => Seq(statementPattern)
                }

                convertedStatementsForWholeStatement ++ additionalStatementsForSubj ++ additionalStatementsForObj
            }

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[QueryPattern] = {

                processStatementPatternFromWhereClause(
                    statementPattern = statementPattern
                )

            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = {

                val filterExpression: TransformedFilterExpression = transformFilterExpression(filterPattern.expression, typeInspection = typeInspectionResult)

                filterExpression.additionalStatements :+ FilterPattern(filterExpression.expression)
            }
        }

        /**
          * Transform the the Knora explicit graph name to GraphDB explicit graph name.
          *
          * @param statement the given statement whose graph name has to be renamed.
          * @return the statement with the renamed graph, if given.
          */
        def transformKnoraExplicitToGraphDBExplicit(statement: StatementPattern): Seq[StatementPattern] = {
            val transformedPattern = statement.copy(
                namedGraph = statement.namedGraph match {
                    case Some(IriRef(OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph)) => Some(IriRef(OntologyConstants.NamedGraphs.GraphDBExplicitNamedGraph))
                    case Some(IriRef(_)) => throw AssertionException(s"Named graphs other than ${OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph} cannot occur in non-triplestore-specific generated search query SPARQL")
                    case None => None
                }
            )

            Seq(transformedPattern)
        }

        class GraphDBSelectToSelectTransformer extends SelectToSelectTransformer {
            def transformStatementInSelect(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[StatementPattern] = {
                transformKnoraExplicitToGraphDBExplicit(statementPattern)
            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)

        }

        class NoInferenceSelectToSelectTransformer extends SelectToSelectTransformer {
            def transformStatementInSelect(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[StatementPattern] = {
                // TODO: if OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph occurs, remove it and use property path syntax to emulate inference.
                Seq(statementPattern)
            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)

        }

        /**
          * Transforms non-triplestore-specific query patterns to GraphDB-specific ones.
          */
        class GraphDBConstructToConstructTransformer extends ConstructToConstructTransformer {
            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[StatementPattern] = {
                transformKnoraExplicitToGraphDBExplicit(statementPattern)
            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
        }

        /**
          * Transforms non-triplestore-specific query patterns for a triplestore that does not have inference enabled.
          */
        class NoInferenceConstructToConstructTransformer extends ConstructToConstructTransformer {
            def transformStatementInConstruct(statementPattern: StatementPattern): Seq[StatementPattern] = Seq(statementPattern)

            def transformStatementInWhere(statementPattern: StatementPattern): Seq[StatementPattern] = {
                // TODO: if OntologyConstants.NamedGraphs.KnoraExplicitNamedGraph occurs, remove it and use property path syntax to emulate inference.
                Seq(statementPattern)
            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = Seq(filterPattern)
        }

        for {
        // Do type inspection and remove type annotations from the WHERE clause.

            typeInspector <- FastFuture.successful(new ExplicitTypeInspectorV2(apiSchema))
            whereClauseWithoutAnnotations: WhereClause = typeInspector.removeTypeAnnotations(inputQuery.whereClause)
            typeInspectionResult: TypeInspectionResult = typeInspector.inspectTypes(inputQuery.whereClause)

            // Preprocess the query to convert API IRIs to internal IRIs and to set inference per statement.

            preprocessedQuery: ConstructQuery = QueryTraverser.transformConstructToConstruct(
                inputQuery = inputQuery.copy(whereClause = whereClauseWithoutAnnotations),
                transformer = new Preprocessor
            )

            // Create a Select prequery. TODO: include OFFSET and LIMIT.
            nonTriplestoreSpecficPrequery: SelectQuery = QueryTraverser.transformConstructToSelect(
                inputQuery = preprocessedQuery.copy(orderBy = inputQuery.orderBy), // TODO: This is a workaround to get Order By into the transformer since the preprocessor does not know about it
                transformer = new NonTriplestoreSpecificConstructToSelectTransformer(typeInspectionResult)
            )

            // Convert the non-triplestore-specific query to a triplestore-specific one.
            triplestoreSpecificQueryPatternTransformerSelect: SelectToSelectTransformer = {
                if (settings.triplestoreType.startsWith("graphdb")) {
                    // GraphDB
                    new GraphDBSelectToSelectTransformer
                } else {
                    // Other
                    new NoInferenceSelectToSelectTransformer
                }
            }

            // Convert the preprocessed query to a non-triplestore-specific query.
            triplestoreSpecificPrequery = QueryTraverser.transformSelectToSelect(
                inputQuery = nonTriplestoreSpecficPrequery,
                transformer = triplestoreSpecificQueryPatternTransformerSelect
            )

            _ = println(triplestoreSpecificPrequery.toSparql)

            prequeryResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(triplestoreSpecificPrequery.toSparql)).mapTo[SparqlSelectResponse]

            nonTriplestoreSpecificQuery: ConstructQuery = QueryTraverser.transformConstructToConstruct(
                inputQuery = preprocessedQuery,
                transformer = new NonTriplestoreSpecificConstructToConstructTransformer(typeInspectionResult)
            )

            // include those IRIs in a VALUES in the CONSTRUCT clause.
            mainResourceVar: QueryVariable = nonTriplestoreSpecificQuery.constructClause.statements.collect {
                case statement if isMainResourceVariable(statement).nonEmpty => isMainResourceVariable(statement).get
            }.headOption.getOrElse(throw SparqlSearchException("Non main resource found in CONSTRUCT query"))

            // a sequence of resource Iris that match the search criteria
            matchingResourceIris: Seq[IRI] = prequeryResponse.results.bindings.map {
                case resultRow: VariableResultsRow =>
                    resultRow.rowMap(mainResourceVar.variableName)
            }

            valuesPattern: ValuesPattern = ValuesPattern(mainResourceVar, matchingResourceIris.map(iri => IriRef(iri)))

            triplestoreSpecificQueryPatternTransformerConstruct: ConstructToConstructTransformer = {
                if (settings.triplestoreType.startsWith("graphdb")) {
                    // GraphDB
                    new GraphDBConstructToConstructTransformer
                } else {
                    // Other
                    new NoInferenceConstructToConstructTransformer
                }
            }

            triplestoreSpecificQuery = QueryTraverser.transformConstructToConstruct(
                inputQuery = nonTriplestoreSpecificQuery.copy(whereClause = nonTriplestoreSpecificQuery.whereClause.copy(patterns = valuesPattern +: nonTriplestoreSpecificQuery.whereClause.patterns)),
                transformer = triplestoreSpecificQueryPatternTransformerConstruct
            )

            // Convert the result to a SPARQL string and send it to the triplestore.

            triplestoreSpecificSparql: String = triplestoreSpecificQuery.toSparql

            _ = println(triplestoreSpecificQuery.toSparql)

            searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(triplestoreSpecificSparql)).mapTo[SparqlConstructResponse]

            // separate resources and value objects
            queryResultsSeparated: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = ConstructResponseUtilV2.splitResourcesAndValueRdfData(constructQueryResults = searchResponse, userProfile = userProfile)

        } yield ReadResourcesSequenceV2(numberOfResources = queryResultsSeparated.size, resources = ConstructResponseUtilV2.createSearchResponse(searchResults = queryResultsSeparated, orderByIri = matchingResourceIris))
    }

    /**
      * Performs a search for resources by their label.
      *
      * @param searchValue the values to search for.
      * @param userProfile the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def searchResourcesByLabelV2(searchValue: String, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        for {
            searchResourceByLabelSparql <- Future(queries.sparql.v2.txt.searchResourceByLabel(
                triplestore = settings.triplestoreType,
                searchTerms = searchValue
            ).toString())

            searchResourceByLabelResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(searchResourceByLabelSparql)).mapTo[SparqlConstructResponse]

            // separate resources and value objects
            queryResultsSeparated = ConstructResponseUtilV2.splitResourcesAndValueRdfData(constructQueryResults = searchResourceByLabelResponse, userProfile = userProfile)

        //_ = println(queryResultsSeparated)

        } yield ReadResourcesSequenceV2(numberOfResources = queryResultsSeparated.size, resources = ConstructResponseUtilV2.createSearchResponse(queryResultsSeparated))


    }
}