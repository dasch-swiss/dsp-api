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
import org.knora.webapi.util.search.v2._
import org.knora.webapi.util.{ConstructResponseUtilV2, DateUtilV1, StringFormatter}

import scala.collection.mutable
import scala.concurrent.Future

/**
  * Constants used in [[SearchResponderV2]].
  */
object SearchResponderV2Constants {

    /**
      * Constants for fulltext query.
      */
    object FullTextSearchConstants {

    }

    /**
      * Constants for extended search.
      */
    object ExtendedSearchConstants {

        val mainResourcePropVar = QueryVariable("mainResourceProp")
        val mainResourceObjectVar = QueryVariable("mainResourceObj")
        val mainResourceValueObject = QueryVariable("mainResourceValueObject")
        val mainResourceValueProp = QueryVariable("mainResourceValueProp")
        val mainResourceValueObjectProp = QueryVariable("mainResourceValueObjectProp")
        val mainResourceValueObjectObj = QueryVariable("mainResourceValueObjectObj")

        val dependentResourcePropVar = QueryVariable("dependentResourceProp")
        val dependentResourceObjectVar = QueryVariable("dependentResourceObj")
        val dependentResourceValueObject = QueryVariable("dependentResourceValueObject")
        val dependentResourceValueProp = QueryVariable("dependentResourceValueProp")
        val dependentResourceValueObjectProp = QueryVariable("dependentResourceValueObjectProp")
        val dependentResourceValueObjectObj = QueryVariable("dependentResourceValueObjectObj")

    }

}

class SearchResponderV2 extends Responder {

    def receive = {
        case FulltextSearchGetRequestV2(searchValue, userProfile) => future2Message(sender(), fulltextSearchV2(searchValue, userProfile), log)
        case ExtendedSearchGetRequestV2(query, userProfile) => future2Message(sender(), extendedSearchV2(inputQuery = query, userProfile = userProfile), log)
        case SearchResourceByLabelRequestV2(searchValue, limitToProject, limitToResourceClass, userProfile) => future2Message(sender(), searchResourcesByLabelV2(searchValue, limitToProject, limitToResourceClass, userProfile), log)
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
            queryResultsSeparated = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = searchResponse, userProfile = userProfile)

        } yield ReadResourcesSequenceV2(numberOfResources = queryResultsSeparated.size, resources = ConstructResponseUtilV2.createSearchResponse(queryResultsSeparated))

    }

    /**
      * Performs an extended search using a Sparql query provided by the user.
      *
      * @param inputQuery  Sparql construct query provided by the client.
      * @param userProfile the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def extendedSearchV2(inputQuery: ConstructQuery, apiSchema: ApiV2Schema = ApiV2Simple, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        if (apiSchema != ApiV2Simple) {
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
                        if (stringFormatter.isExternalEntityIri(iriRef.iri)) {
                            IriRef(stringFormatter.externalToInternalEntityIri(iriRef.iri, () => throw BadRequestException(s"${iriRef.iri} is not a valid external knora-api entity Iri")))
                        } else {
                            IriRef(stringFormatter.toIri(iriRef.iri, () => throw BadRequestException(s"$iriRef is not a valid IRI")))
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
                        val externalIri = if (stringFormatter.isInternalEntityIri(iriRef.iri)) {
                            stringFormatter.internalEntityIriToApiV2SimpleEntityIri(iriRef.iri, () => throw BadRequestException(s"${iriRef.iri} is not a valid internal knora-api entity Iri"))
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

                // make sure that type info has not been processed yet
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

            /**
              * Represents a transformed Filter expression and additional statement patterns that possibly had to be created during transformation.
              *
              * @param expression           the transformed Filter expression.
              * @param additionalStatements additionally created statement patterns.
              */
            case class TransformedFilterExpression(expression: Expression, additionalStatements: Seq[StatementPattern] = Seq.empty[StatementPattern])

            /**
              * Transforms a Filter expression provided in the input query (knora-api simple) into a knora-base compliant Filter expression.
              *
              * @param filterExpression the Filter expression to be transformed.
              * @param typeInspection   the results of type inspection.
              * @return a [[TransformedFilterExpression]].
              */
            def transformFilterExpression(filterExpression: Expression, typeInspection: TypeInspectionResult): TransformedFilterExpression = {

                filterExpression match {

                    case filterCompare: CompareExpression =>

                        // left argument of a CompareExpression is expected to be a QueryVariable
                        val queryVar: QueryVariable = filterCompare.leftArg match {

                            case queryVar: QueryVariable => queryVar

                            case other => throw SparqlSearchException(s"Left argument of a Filter CompareExpression is expected to be a QueryVariable, but $other is given")
                        }

                        // make a key to look up information in type inspection results
                        val queryVarTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(queryVar)

                        // get information about the queryVar's type
                        if (queryVarTypeInfoKey.nonEmpty && (typeInspection.typedEntities contains queryVarTypeInfoKey.get)) {

                            // get type information for queryVar
                            val typeInfo: SparqlEntityTypeInfo = typeInspection.typedEntities(queryVarTypeInfoKey.get)

                            // check if queryVar represents a property or a value
                            typeInfo match {

                                case propInfo: PropertyTypeInfo =>

                                    // left arg queryVar is a variable representing a property
                                    // therefore the right argument must be an Iri restricting the property variable to a certain property
                                    filterCompare.rightArg match {
                                        case iriRef: IriRef =>

                                            // make sure that the comparison operator is a `CompareExpressionOperator.EQUALS`
                                            if (filterCompare.operator != CompareExpressionOperator.EQUALS) throw SparqlSearchException(s"Comparison operator in a CompareExpression for a property type is expected to be ${CompareExpressionOperator.EQUALS}, but ${filterCompare.operator} given. For negations use 'FILTER NOT EXISTS' ")

                                            TransformedFilterExpression(CompareExpression(filterCompare.leftArg, filterCompare.operator, filterCompare.rightArg))

                                        case other => throw SparqlSearchException(s"right argument of CompareExpression is expected to be an Iri representing a property, but $other is given")
                                    }

                                case nonPropInfo: NonPropertyTypeInfo =>

                                    // the left arg queryVar is a variable representing a value
                                    // get the internal Iri of the value type, if possible (xsd types are not internal types).
                                    val typeIriInternal = if (stringFormatter.isExternalEntityIri(nonPropInfo.typeIri)) {
                                        stringFormatter.externalToInternalEntityIri(nonPropInfo.typeIri, () => throw BadRequestException(s"${nonPropInfo.typeIri} is not a valid external knora-api entity Iri"))
                                    } else {
                                        nonPropInfo.typeIri
                                    }

                                    // depending on the value type, transform the given Filter expression.
                                    // add an extra level by getting the value literal from the value object.
                                    // queryVar refers to the value object, for the value literal an extra variable has to be created, taking its type into account.
                                    typeIriInternal match {

                                        case OntologyConstants.Xsd.Integer =>

                                            // make sure that the right argument is an integer literal
                                            val integerLiteral: XsdLiteral = filterCompare.rightArg match {
                                                case intLiteral: XsdLiteral if intLiteral.datatype == OntologyConstants.Xsd.Integer => intLiteral

                                                case other => throw SparqlSearchException(s"right argument in CompareExpression for integer property was expected to be an integer literal, but $other is given.")
                                            }

                                            // create a variable representing the integer literal
                                            val intValHasInteger: QueryVariable = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasInteger)

                                            // add this variable to the collection of additionally created variables (needed for sorting in the prequery)
                                            valueVariablesCreatedInFilters.put(queryVar, intValHasInteger)

                                            TransformedFilterExpression(
                                                CompareExpression(intValHasInteger, filterCompare.operator, integerLiteral),
                                                Seq(
                                                    // connects the value object with the value literal
                                                    StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasInteger), intValHasInteger)
                                                )
                                            )

                                        case OntologyConstants.Xsd.Decimal =>

                                            // make sure that the right argument is a decimal or integer literal
                                            val decimalLiteral: XsdLiteral = filterCompare.rightArg match {
                                                case decimalLiteral: XsdLiteral if decimalLiteral.datatype == OntologyConstants.Xsd.Decimal || decimalLiteral.datatype == OntologyConstants.Xsd.Integer => decimalLiteral

                                                case other => throw SparqlSearchException(s"right argument in CompareExpression for decimal property was expected to be a decimal or an integer literal, but $other is given.")
                                            }

                                            // create a variable representing the decimal literal
                                            val decimalValHasDecimal = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasDecimal)

                                            // add this variable to the collection of additionally created variables (needed for sorting in the prequery)
                                            valueVariablesCreatedInFilters.put(queryVar, decimalValHasDecimal)

                                            TransformedFilterExpression(
                                                CompareExpression(decimalValHasDecimal, filterCompare.operator, decimalLiteral),
                                                Seq(
                                                    // connects the value object with the value literal
                                                    StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasDecimal), decimalValHasDecimal)
                                                )
                                            )

                                        case OntologyConstants.Xsd.Boolean =>

                                            // make sure that the right argument is a boolean literal
                                            val booleanLiteral: XsdLiteral = filterCompare.rightArg match {
                                                case booleanLiteral: XsdLiteral if booleanLiteral.datatype == OntologyConstants.Xsd.Boolean => booleanLiteral

                                                case other => throw SparqlSearchException(s"right argument in CompareExpression for boolean property was expected to be a boolean literal, but $other is given.")
                                            }

                                            // create a variable representing the boolean literal
                                            val booleanValHasBoolean = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasBoolean)

                                            // add this variable to the collection of additionally created variables (needed for sorting in the prequery)
                                            valueVariablesCreatedInFilters.put(queryVar, booleanValHasBoolean)

                                            // check if operator is supported for boolean
                                            if (!(filterCompare.operator.equals(CompareExpressionOperator.EQUALS) || filterCompare.operator.equals(CompareExpressionOperator.NOT_EQUALS))) {
                                                throw SparqlSearchException(s"Filter expressions for a boolean value supports the following operators: ${CompareExpressionOperator.EQUALS}, ${CompareExpressionOperator.NOT_EQUALS}, but ${filterCompare.operator} given")
                                            }

                                            TransformedFilterExpression(
                                                CompareExpression(booleanValHasBoolean, filterCompare.operator, booleanLiteral),
                                                Seq(
                                                    // connects the value object with the value literal
                                                    StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasBoolean), booleanValHasBoolean)
                                                )
                                            )

                                        case OntologyConstants.Xsd.String =>

                                            // make sure that the right argument is a string literal
                                            val stringLiteral: XsdLiteral = filterCompare.rightArg match {
                                                case strLiteral: XsdLiteral if strLiteral.datatype == OntologyConstants.Xsd.String => strLiteral

                                                case other => throw SparqlSearchException(s"right argument in CompareExpression for string property was expected to be a string literal, but $other is given.")
                                            }

                                            // create a variable representing the string literal
                                            val textValHasString = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasString)

                                            // add this variable to the collection of additionally created variables (needed for sorting in the prequery)
                                            valueVariablesCreatedInFilters.put(queryVar, textValHasString)

                                            // check if operator is supported for string operations
                                            if (!(filterCompare.operator.equals(CompareExpressionOperator.EQUALS) || filterCompare.operator.equals(CompareExpressionOperator.NOT_EQUALS))) {
                                                throw SparqlSearchException(s"Filter expressions for a string value supports the following operators: ${CompareExpressionOperator.EQUALS}, ${CompareExpressionOperator.NOT_EQUALS}, but ${filterCompare.operator} given")
                                            }

                                            TransformedFilterExpression(
                                                CompareExpression(textValHasString, filterCompare.operator, stringLiteral),
                                                Seq(
                                                    // connects the value object with the value literal
                                                    StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasString), textValHasString)
                                                )
                                            )

                                        case OntologyConstants.KnoraBase.Date =>

                                            // make sure that the right argument is a string literal (dates are represented as knora date strings in knora-api simple)
                                            val dateStringLiteral: _root_.org.knora.webapi.util.search.XsdLiteral = filterCompare.rightArg match {
                                                case dateStrLiteral: XsdLiteral if dateStrLiteral.datatype == OntologyConstants.Xsd.String => dateStrLiteral

                                                case other => throw SparqlSearchException(s"right argument in CompareExpression for date property was expected to be a string literal representing a date, but $other is given.")
                                            }

                                            // validate Knora  date string
                                            val dateStr: String = stringFormatter.toDate(dateStringLiteral.value, () => throw BadRequestException(s"${dateStringLiteral.value} is not a valid date string"))

                                            val date: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(dateStr)

                                            // create a variable representing the period's start
                                            val dateValueHasStartVar = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasStartJDN)

                                            // sort dates by their period's start (in the prequery)
                                            valueVariablesCreatedInFilters.put(queryVar, dateValueHasStartVar)

                                            // create a variable representing the period's end
                                            val dateValueHasEndVar = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasEndJDN)

                                            // connects the value object with the periods start variable
                                            val dateValStartStatement = StatementPattern.makeExplicit(subj = queryVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStartJDN), obj = dateValueHasStartVar)

                                            // connects the value object with the periods end variable
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

                                                    // no overlap in considered as inequality (negation of equality)
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

                                        case other => throw NotImplementedException(s"Value type $other not supported in FilterExpression")

                                    }

                            }


                        } else {
                            throw SparqlSearchException(s"type information about $queryVar is missing")
                        }


                    case filterOr: OrExpression =>
                        // recursively call this method for both arguments
                        val filterExpressionLeft = transformFilterExpression(filterOr.leftArg, typeInspection)
                        val filterExpressionRight = transformFilterExpression(filterOr.rightArg, typeInspection)

                        // recreate Or expression and include additional statements
                        TransformedFilterExpression(
                            OrExpression(filterExpressionLeft.expression, filterExpressionRight.expression),
                            filterExpressionLeft.additionalStatements ++ filterExpressionRight.additionalStatements
                        )


                    case filterAnd: AndExpression =>
                        // recursively call this method for both arguments
                        val filterExpressionLeft = transformFilterExpression(filterAnd.leftArg, typeInspection)
                        val filterExpressionRight = transformFilterExpression(filterAnd.rightArg, typeInspection)

                        // recreate And expression and include additional statements
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

            // Contains the variable representing the main resource: knora-base:isMainResource
            private var mainResourceVariable: Option[QueryVariable] = None

            // Contains the variables of dependent resources
            private var dependentResourceVariables = mutable.Set.empty[QueryVariable]

            /**
              * Creates additional statements for a non property type (e.g., a resource).
              *
              * @param nonPropertyTypeInfo type information about non property type.
              * @param inputEntity         the [[Entity]] to make the statements about.
              * @return a sequence of [[QueryPattern]] representing the additional statements.
              */
            def createAdditionalStatementsForNonPropertyType(nonPropertyTypeInfo: NonPropertyTypeInfo, inputEntity: Entity): Seq[QueryPattern] = {

                val typeIriInternal = if (stringFormatter.isExternalEntityIri(nonPropertyTypeInfo.typeIri)) {
                    stringFormatter.externalToInternalEntityIri(nonPropertyTypeInfo.typeIri, () => throw BadRequestException(s"${nonPropertyTypeInfo.typeIri} is not a valid external knora-api entity Iri"))
                } else {
                    nonPropertyTypeInfo.typeIri
                }

                if (typeIriInternal == OntologyConstants.KnoraBase.Resource) {

                    // inputEntity is either source or target of a linking property
                    // create additional statements in order to query permissions and other information for a resource

                    // add the inputEntity (a variable representing a resource) to the SELECT
                    inputEntity match {
                        case queryVar: QueryVariable =>
                            // make sure that this is not the mainVar
                            mainResourceVariable match {
                                case Some(mainVar: QueryVariable) =>

                                    if (mainVar != queryVar) {
                                        // it is a variable representing a dependent resource
                                        dependentResourceVariables += queryVar
                                    }

                                case None =>

                            }

                        case _ =>
                    }

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
                val objectIri = if (stringFormatter.isExternalEntityIri(propertyTypeInfo.objectTypeIri)) {
                    stringFormatter.externalToInternalEntityIri(propertyTypeInfo.objectTypeIri, () => throw BadRequestException(s"${propertyTypeInfo.objectTypeIri} is not a valid external knora-api entity Iri"))
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
                    case Some(mainVar: QueryVariable) => Seq(mainVar) ++ dependentResourceVariables

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
                                        val internalTypeIri = if (stringFormatter.isExternalEntityIri(nonPropertyTypeInfo.typeIri)) {
                                            IriRef(stringFormatter.externalToInternalEntityIri(nonPropertyTypeInfo.typeIri, () => throw BadRequestException(s"${nonPropertyTypeInfo.typeIri} is not a valid external knora-api entity Iri")))
                                        } else {
                                            IriRef(stringFormatter.toIri(nonPropertyTypeInfo.typeIri, () => throw BadRequestException(s"${nonPropertyTypeInfo.typeIri} is not a valid IRI")))
                                        }
                                        literalTypesToValueTypeIris.getOrElse(internalTypeIri.iri, throw SparqlSearchException(s"Type ${internalTypeIri.iri} is not supported in ORDER BY"))

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

                // main resource variable as order by criterion
                val orderByMainResVar: OrderCriterion = OrderCriterion(
                    queryVariable = mainResourceVariable.getOrElse(throw SparqlSearchException("No ${OntologyConstants.KnoraBase.IsMainResource} found in CONSTRUCT query.")),
                    isAscending = true
                )

                // dependent resource variables as order by criteria
                val orderByDependentResVars: Seq[OrderCriterion] = dependentResourceVariables.map {
                    (resVar: QueryVariable) =>
                        OrderCriterion(
                            queryVariable = resVar,
                            isAscending = true
                        )
                }.toSeq

                // order by: user provided variables, main resource variable, dependent resource variables
                // all variables must be included in the order by statements to make the results predictable for paging
                // in case more than one value is returned for a dependent resource variable, there will be more than one row for the same main resource variable
                transformedOrderBy.copy(
                    orderBy = transformedOrderBy.orderBy ++ (orderByMainResVar +: orderByDependentResVars)
                )
            }

            /**
              * Gets the maximal amount of result rows to be returned by the prequery.
              *
              * @return the LIMIT, if any.
              */
            def getLimit: Int = {
                // get LIMIT from settings
                settings.v2ExtendedSearchResultsPerPage
            }

            /**
              * Gets the OFFSET to be used in the prequery (needed for paging).
              *
              * @param inputQueryOffset the OFFSET provided in the input query.
              * @param limit            the amount of result rows to be returned by the prequery
              * @return the OFFSET.
              */
            def getOffset(inputQueryOffset: Long, limit: Int): Long = {

                if (inputQueryOffset < 0) throw AssertionException("Negative OFFSET is illegal.")

                if (inputQueryOffset == 0) {
                    // first page, offset is zero
                    0
                } else {
                    // subsequent page -> multiply offset with limit
                    // for instance: the user requests offset 1, meaning that he wants to get the second page of results.
                    // the OFFSET equals the amount of previous pages and has to be multiplied with the LIMIT used to get the number of rows.
                    inputQueryOffset * limit
                }

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

        /**
          * Creates the main query to be sent to the triplestore.
          * Requests two sets of information: about the main resources and the dependent resources.
          *
          * @param mainResourceVar                    the variable representing the main resources.
          * @param valuesPatternForMainResources      Iris of the main reource variable.
          * @param dependentResourceVar               the variable representing the dependent resources.
          * @param valuesPatternForDependentResources Iris of the dependent resources.
          * @return the main [[ConstructQuery]] query to be executed.
          */
        def createMainQuery(mainResourceVar: QueryVariable, valuesPatternForMainResources: ValuesPattern, dependentResourceVar: QueryVariable, valuesPatternForDependentResources: ValuesPattern): ConstructQuery = {

            import SearchResponderV2Constants.ExtendedSearchConstants._

            val wherePatternsForMainResources = Seq(
                valuesPatternForMainResources,
                StatementPattern.makeExplicit(subj = mainResourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)),
                StatementPattern.makeExplicit(subj = mainResourceVar, pred = mainResourcePropVar, obj = mainResourceObjectVar),
                StatementPattern.makeInferred(subj = mainResourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = mainResourceValueObject),
                StatementPattern.makeExplicit(subj = mainResourceVar, pred = mainResourceValueProp, obj = mainResourceValueObject),
                StatementPattern.makeExplicit(subj = mainResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)),
                StatementPattern.makeExplicit(subj = mainResourceValueObject, pred = mainResourceValueObjectProp, obj = mainResourceValueObjectObj)
            )

            val wherePatternsForDependentResources = Seq(
                valuesPatternForDependentResources,
                StatementPattern.makeExplicit(subj = dependentResourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)),
                StatementPattern.makeExplicit(subj = dependentResourceVar, pred = dependentResourcePropVar, obj = dependentResourceObjectVar),
                StatementPattern.makeInferred(subj = dependentResourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = dependentResourceValueObject),
                StatementPattern.makeExplicit(subj = dependentResourceVar, pred = dependentResourceValueProp, obj = dependentResourceValueObject),
                StatementPattern.makeExplicit(subj = dependentResourceValueObject, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)),
                StatementPattern.makeExplicit(subj = dependentResourceValueObject, pred = dependentResourceValueObjectProp, obj = dependentResourceValueObjectObj)
            )

            ConstructQuery(
                constructClause = ConstructClause(
                    Seq(
                        StatementPattern(subj = mainResourceVar, pred = IriRef(OntologyConstants.KnoraBase.IsMainResource), obj = XsdLiteral(value = "true", datatype = OntologyConstants.Xsd.Boolean)),
                        StatementPattern(subj = mainResourceVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.Resource)),
                        StatementPattern(subj = mainResourceVar, pred = mainResourcePropVar, obj = mainResourceObjectVar),
                        StatementPattern(subj = mainResourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = mainResourceValueObject),
                        StatementPattern(subj = mainResourceVar, pred = mainResourceValueProp, obj = mainResourceValueObject),
                        StatementPattern(subj = mainResourceValueObject, pred = mainResourceValueObjectProp, obj = mainResourceValueObjectObj),
                        StatementPattern(subj = dependentResourceVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.Resource)),
                        StatementPattern(subj = dependentResourceVar, pred = dependentResourcePropVar, obj = dependentResourceObjectVar),
                        StatementPattern(subj = dependentResourceVar, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = dependentResourceValueObject),
                        StatementPattern(subj = dependentResourceVar, pred = dependentResourceValueProp, obj = dependentResourceValueObject),
                        StatementPattern(subj = dependentResourceValueObject, pred = dependentResourceValueObjectProp, obj = dependentResourceValueObjectObj)
                    )
                ),
                whereClause = WhereClause(Seq(UnionPattern(Seq(wherePatternsForMainResources, wherePatternsForDependentResources))))
            )
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

            // Create a Select prequery
            nonTriplestoreSpecficPrequery: SelectQuery = QueryTraverser.transformConstructToSelect(
                inputQuery = preprocessedQuery.copy(orderBy = inputQuery.orderBy, offset = inputQuery.offset), // TODO: This is a workaround to get Order By and OFFSET into the transformer since the preprocessor does not know about it
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

            // _ = println(triplestoreSpecificPrequery.toSparql)

            prequeryResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(triplestoreSpecificPrequery.toSparql)).mapTo[SparqlSelectResponse]

            // variables returned by prequery
            prequeryResultsVariableNames: Seq[String] = prequeryResponse.head.vars

            // variable representing the main resources
            mainResourceVar: QueryVariable = QueryVariable(prequeryResultsVariableNames.headOption.getOrElse(throw SparqlSearchException("SELECT prequery returned no variable")))

            // a sequence of resource Iris that match the search criteria
            // attention: no permission checking has been done so far
            mainResourceIris: Seq[IRI] = prequeryResponse.results.bindings.map {
                case resultRow: VariableResultsRow =>
                    resultRow.rowMap(mainResourceVar.variableName)
            }

            // a ValuePattern representing all the possible Iris for the main resource variable
            valuesPatternForMainResources: ValuesPattern = ValuesPattern(mainResourceVar, mainResourceIris.map(iri => IriRef(iri)).toSet)

            // the tail of prequeryResultsVariableNames contains variables representing dependent resources
            dependentResourceVariables: Seq[String] = prequeryResultsVariableNames.tail

            // get all the Iris for variables representing dependent resources
            // iterate over all variables representing dependent resources
            dependentResourceIrisFromPrequery: Set[IRI] = dependentResourceVariables.foldLeft(Set.empty[IRI]) {
                case (acc, resVar) =>
                    // collect all the values for the current var from prequery response
                    val resIris: Seq[IRI] = prequeryResponse.results.bindings.map {
                        case resultRow: VariableResultsRow =>
                            resultRow.rowMap(resVar)
                    }

                    acc ++ resIris
            }

            // the user may have defined Iris of dependent resources in the input query (type annotations)
            dependentResourceIrisFromTypeInspection: Set[IRI] = typeInspectionResult.typedEntities.collect {
                case (iri: TypeableIri, nonPropTypeInfo: NonPropertyTypeInfo) => iri.iri
            }.toSet

            // variable representing dependent resources
            dependentResourceVar = QueryVariable("dependentResource")

            // a ValuePattern representing all the possible Iris for dependent resources
            valuesPatternForDependentResources = ValuesPattern(dependentResourceVar, (dependentResourceIrisFromPrequery ++ dependentResourceIrisFromTypeInspection).map(iri => IriRef(iri)))

            // create the main query
            // it is a Union of two sets: the main resources and the dependent resources
            mainQuery = createMainQuery(
                mainResourceVar = mainResourceVar,
                valuesPatternForMainResources = valuesPatternForMainResources,
                dependentResourceVar = dependentResourceVar,
                valuesPatternForDependentResources = valuesPatternForDependentResources
            )

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
                inputQuery = mainQuery,
                transformer = triplestoreSpecificQueryPatternTransformerConstruct
            )

            // Convert the result to a SPARQL string and send it to the triplestore.
            triplestoreSpecificSparql: String = triplestoreSpecificQuery.toSparql

            // _ = println(triplestoreSpecificQuery.toSparql)

            searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(triplestoreSpecificSparql)).mapTo[SparqlConstructResponse]

            // separate main resources and value objects (dependent resources are nested)
            queryResultsSeparated: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = searchResponse, userProfile = userProfile)

            // TODO: sort out those properties that the user did not ask for (look at preprocessedQuery.inputQuery)
            // TODO: check that all properties from the Where clause are still in the results (after permission checks) -> a resource should only be returned if the user has the permissions to see all the properties contained in the Where clause

            // TODO: find a way to check for the property instance if a property has several values. For performance reasons, we query all the properties of a resource. How can we find the correct instance of a property?

        } yield ReadResourcesSequenceV2(numberOfResources = queryResultsSeparated.size, resources = ConstructResponseUtilV2.createSearchResponse(searchResults = queryResultsSeparated, orderByResourceIri = mainResourceIris))
    }

    /**
      * Performs a search for resources by their rdf:label.
      *
      * @param searchValue the values to search for.
      * @param limitToProject limit search to given project.
      * @param limitToResourceClass limit search to given resource class.
      * @param userProfile the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def searchResourcesByLabelV2(searchValue: String, limitToProject: Option[IRI], limitToResourceClass: Option[IRI], userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        for {
            searchResourceByLabelSparql <- Future(queries.sparql.v2.txt.searchResourceByLabel(
                triplestore = settings.triplestoreType,
                searchTerms = searchValue,
                limitToProject = limitToProject,
                limitToResourceClass = limitToResourceClass
            ).toString())

            searchResourceByLabelResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(searchResourceByLabelSparql)).mapTo[SparqlConstructResponse]

            // separate resources and value objects
            queryResultsSeparated = ConstructResponseUtilV2.splitMainResourcesAndValueRdfData(constructQueryResults = searchResourceByLabelResponse, userProfile = userProfile)

        //_ = println(queryResultsSeparated)

        } yield ReadResourcesSequenceV2(numberOfResources = queryResultsSeparated.size, resources = ConstructResponseUtilV2.createSearchResponse(queryResultsSeparated))


    }
}