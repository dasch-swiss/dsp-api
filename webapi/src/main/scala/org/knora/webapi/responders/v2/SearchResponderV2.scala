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
import org.knora.webapi.messages.store.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse}
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
            protected val valueVariablesUsedInFilters = mutable.Map.empty[QueryVariable, QueryVariable]

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
              * @param entity               the entity to be taken into consideration (a statement's subject or object).
              * @param typeInspection       type information.
              * @param processedTypeInfo    the keys of type information that have already been looked at.
              * @param conversionFuncForNonPropertyType       the function to use to create additional statements.
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

            var mainResourceVariable: Option[QueryVariable] = None

            // A Map of XSD types to the corresponding knora-base value predicates that point to literals. This allows us to generate a variable name for using a value in an ORDER BY.
            val literalTypesToValueTypeIris: Map[IRI, IRI] = Map(
                OntologyConstants.Xsd.Integer -> OntologyConstants.KnoraBase.ValueHasInteger,
                OntologyConstants.Xsd.Decimal -> OntologyConstants.KnoraBase.ValueHasDecimal,
                OntologyConstants.Xsd.Boolean -> OntologyConstants.KnoraBase.ValueHasBoolean,
                OntologyConstants.Xsd.String -> OntologyConstants.KnoraBase.ValueHasString,
                OntologyConstants.KnoraBase.Date -> OntologyConstants.KnoraBase.ValueHasStartJDN
            )

            /**
              * Creates additional statements for a non property type (e.g., a resource).
              *
              * @param nonPropertyTypeInfo type information about non property type.
              * @param inputEntity  the [[Entity]] to make the statements about.
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

                Seq(statementPattern)
            }

            private def processStatementPatternFromWhereClause(statementPattern: StatementPattern): Seq[QueryPattern] = {

                // look at the statement's subject, predicate, and object and generate additional statements if needed based on the given type information.
                // transform the originally given statement if necessary when processing the predicate

                // create `TypeableEntity` (keys in `typeInspectionResult`) from the given statement's elements

                val predTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(statementPattern.pred)

                // check if there exists type information for the given statement's subject
                val additionalStatementsForSubj: Seq[QueryPattern] = checkForNonPropertyTypeInfoForEntity(statementPattern.subj, typeInspectionResult, processedTypeInformationKeysWhereClause, createAdditionalStatementsForNonPropertyType)

                // check if there exists type information for the given statement's object
                val additionalStatementsForObj: Seq[QueryPattern] = checkForNonPropertyTypeInfoForEntity(statementPattern.obj, typeInspectionResult, processedTypeInformationKeysWhereClause, createAdditionalStatementsForNonPropertyType)

                // Add additional statements based on the whole input statement, e.g. to deal with the value object or the link value, and transform the original statement.
                val additionalStatementsForWholeStatement: Seq[QueryPattern] = if (predTypeInfoKey.nonEmpty && (typeInspectionResult.typedEntities contains predTypeInfoKey.get)) {
                    // process type information for the predicate into additional statements

                    val propTypeInfo = typeInspectionResult.typedEntities(predTypeInfoKey.get) match {
                        case propInfo: PropertyTypeInfo => propInfo

                        case _ => throw AssertionException(s"PropertyTypeInfo expected for ${predTypeInfoKey.get}")
                    }

                    val additionalStatements = convertStatementForPropertyType(
                        propertyTypeInfo = propTypeInfo,
                        statementPattern = statementPattern
                    )

                    additionalStatements

                } else {
                    // no type information given and thus no further processing needed, just return the originally given statement (e.g., rdf:type)
                    Seq(statementPattern)
                }


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

                statementPattern.pred match {
                    case IriRef(OntologyConstants.KnoraBase.IsMainResource) =>
                        statementPattern.obj match {
                            case XsdLiteral("true", OntologyConstants.Xsd.Boolean) =>
                                statementPattern.subj match {
                                    case queryVariable: QueryVariable => mainResourceVariable = Some(queryVariable)
                                    case _ => throw SparqlSearchException(s"The subject of ${OntologyConstants.KnoraBase.IsMainResource} must be a variable") // TODO: use the knora-api predicate in the error message?
                                }

                            case _ => ()
                        }

                    case _ => ()
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
                Seq.empty[QueryPattern]
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

                        valueVariablesUsedInFilters.get(criterion.queryVariable) match {
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


            /**
              * Create additional statements for the given non property type information.
              *
              * @param nonPropertyTypeInfo type information about the statement provided by the user.
              * @param inputEntity         the entity to create additional statements for.
              * @return a sequence of [[QueryPattern]] representing additional statements created from the given type information.
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
                        StatementPattern.makeExplicit(subj = inputEntity, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)),
                        StatementPattern.makeExplicit(subj = inputEntity, pred = IriRef(OntologyConstants.Rdfs.Label), obj = createUniqueVariableNameFromEntityAndProperty(inputEntity, OntologyConstants.Rdfs.Label)),
                        StatementPattern.makeExplicit(subj = inputEntity, pred = IriRef(OntologyConstants.Rdf.Type), obj = createUniqueVariableNameFromEntityAndProperty(inputEntity, OntologyConstants.Rdf.Type)),
                        StatementPattern.makeExplicit(subj = inputEntity, pred = IriRef(OntologyConstants.KnoraBase.AttachedToUser), obj = createUniqueVariableNameFromEntityAndProperty(inputEntity, OntologyConstants.KnoraBase.AttachedToUser)),
                        StatementPattern.makeExplicit(subj = inputEntity, pred = IriRef(OntologyConstants.KnoraBase.HasPermissions), obj = createUniqueVariableNameFromEntityAndProperty(inputEntity, OntologyConstants.KnoraBase.HasPermissions)),
                        StatementPattern.makeExplicit(subj = inputEntity, pred = IriRef(OntologyConstants.KnoraBase.AttachedToProject), obj = createUniqueVariableNameFromEntityAndProperty(inputEntity, OntologyConstants.KnoraBase.AttachedToProject))
                    )
                } else {
                    // inputEntity is target of a value property
                    // properties are handled by `convertStatementForPropertyType`, no processing needed here

                    Seq.empty[QueryPattern]
                }
            }

            /**
              * Create the necessary statements to represent a value object, based on the statement pointing to the value from a resource via a property.
              *
              * @param statementPattern the statement pattern pointing to the value (its subject is a resource, its predicate a property, and its object a value)
              * @param valueObject      the [[Entity]] representing the value object.
              * @param valueType        the type of the value object.
              * @return a sequence of [[StatementPattern]] representing the value object.
              */
            def createStatementPatternsForValueObject(statementPattern: StatementPattern, valueObject: Entity, valueType: IRI): Seq[StatementPattern] = {

                // variable referring to the value object
                val valueObjPropVar = createUniqueVariableFromStatement(statementPattern, "voProp") // A variable representing the property pointing to the value object
                val valueObjPred = createUniqueVariableFromStatement(statementPattern, "voPred") // A variable representing the predicates of the value object
                val valueObjObj = createUniqueVariableFromStatement(statementPattern, "voObj") // A variable representing the objects of the value object

                Seq(
                    StatementPattern.makeInferred(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = valueObject), // include knora-base:hasValue pointing to the value object, because the construct clause needs it
                    StatementPattern.makeInferred(subj = statementPattern.subj, pred = statementPattern.pred, obj = valueObject), // TODO: do not include the originally given property in the Construct clause because inference is used
                    StatementPattern.makeExplicit(subj = statementPattern.subj, pred = valueObjPropVar, obj = valueObject), // the actually matching property
                    StatementPattern.makeExplicit(subj = valueObject, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)), // make sure the value object is not marked as deleted
                    StatementPattern.makeExplicit(subj = valueObject, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(valueType)),
                    StatementPattern.makeExplicit(subj = valueObject, pred = valueObjPred, obj = valueObjObj)
                )
            }

            /**
              * Create additional statements for the given property type information.
              *
              * @param propertyTypeInfo type information about the statement provided by the user.
              * @param statementPattern statement provided by the user.
              * @return a sequence of [[QueryPattern]] representing additional statements created from the given type information.
              */
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

                        // the given statement pattern's object is of type resource
                        // this means that the predicate of the statement pattern is a linking property
                        // create statements in order to query the link value describing the link in question

                        // variable referring to the link's value object (reification)
                        val linkValueVar = createUniqueVariableFromStatement(statementPattern, "linkObj") // A variable representing the reification
                        val linkPropVar = createUniqueVariableFromStatement(statementPattern, "linkProp") // A variable representing the explicit property that actually points to the target resource
                        val linkValuePropVar = createUniqueVariableFromStatement(statementPattern, "linkValueProp") // A variable representing the explicit property that actually points to the reification
                        val linkValuePredVar = createUniqueVariableFromStatement(statementPattern, "linkValuePred") // A variable representing a predicate of the reification
                        val linkValueObjVar = createUniqueVariableFromStatement(statementPattern, "linkValueObj") // A variable representing a predicate of the reification

                        // make sure that the statement's object is an Iri
                        statementPattern.obj match {
                            case iriRef: IriRef => ()
                            case other => throw SparqlSearchException(s"Object of a linking statement must be an Iri, but $other given.")
                        }

                        // TODO: make use of createStatementPatternsForValueObject if possible
                        Seq(statementPattern, // keep the original statement pointing from the source to the target resource, using inference
                            StatementPattern.makeExplicit(subj = statementPattern.subj, pred = linkPropVar, obj = statementPattern.obj), // find out what the actual link property is
                            StatementPattern.makeInferred(subj = statementPattern.subj, pred = IriRef(OntologyConstants.KnoraBase.HasValue), obj = linkValueVar), // include knora-base:hasValue pointing to the link value, because the construct clause needs it
                            StatementPattern.makeExplicit(subj = statementPattern.subj, pred = linkValuePropVar, obj = linkValueVar), // find out what the actual link value property is
                            StatementPattern.makeExplicit(subj = linkValueVar, pred = IriRef(OntologyConstants.KnoraBase.IsDeleted), obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)), // ensure the link value isn't deleted
                            StatementPattern.makeExplicit(subj = linkValueVar, pred = IriRef(OntologyConstants.Rdf.Type), obj = IriRef(OntologyConstants.KnoraBase.LinkValue)), // it's a link value
                            StatementPattern.makeExplicit(subj = linkValueVar, pred = IriRef(OntologyConstants.Rdf.Subject), obj = statementPattern.subj), // the rdf:subject of the link value must be the source resource
                            StatementPattern.makeExplicit(subj = linkValueVar, pred = IriRef(OntologyConstants.Rdf.Predicate), obj = linkPropVar), // the rdf:predicate of the link value must be the link property
                            StatementPattern.makeExplicit(subj = linkValueVar, pred = IriRef(OntologyConstants.Rdf.Object), obj = statementPattern.obj), // the rdf:object of the link value must be the target resource
                            StatementPattern.makeExplicit(subj = linkValueVar, pred = linkValuePredVar, obj = linkValueObjVar) // get any other statements about the link value
                        )
                    }

                    case OntologyConstants.Xsd.Integer => {

                        // the given statement pattern's object is of type xsd:integer
                        // this means that the predicate of the statement pattern is a value property
                        // the original statement is not included because it represents the value as a literal (in Knora, it is an object)

                        // check if the statement pattern's object is a literal or a variable
                        statementPattern.obj match {

                            // create statements in order to query the integer value object

                            case integerLiteral: XsdLiteral =>

                                // make sure the literal is of type integer
                                if (integerLiteral.datatype != OntologyConstants.Xsd.Integer) {
                                    throw SparqlSearchException(s"an integer literal was expected, but ${integerLiteral.datatype} given")
                                }

                                val valueObjVar = createUniqueVariableFromStatement(statementPattern, "voVar") // A variable representing the value object

                                val valueObject = createStatementPatternsForValueObject(
                                    statementPattern = statementPattern,
                                    valueObject = valueObjVar,
                                    valueType = OntologyConstants.KnoraBase.IntValue
                                )

                                // match the given literal value with the integer value object's `valueHasInteger`
                                valueObject :+ StatementPattern.makeExplicit(subj = valueObjVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasInteger), obj = integerLiteral) // TODO: this is for the Where clause only (restriction)


                            case integerVar: QueryVariable =>

                                // just create the statements to query the integer value object, possibly a Filter statement exists to restrict the integer values
                                createStatementPatternsForValueObject(
                                    statementPattern,
                                    integerVar, // user provided variable used to represent the value object
                                    OntologyConstants.KnoraBase.IntValue
                                )

                            case other => throw SparqlSearchException(s"Not supported as an object of type integer: $other")

                        }
                    }

                    case OntologyConstants.Xsd.String => {

                        // the given statement pattern's object is of type xsd:string
                        // this means that the predicate of the statement pattern is a value property
                        // the original statement is not included because it represents the value as a literal (in Knora, it is an object)

                        // check if the statement pattern's object is a literal or a variable
                        statementPattern.obj match {

                            // create statements in order to query the text value object

                            case stringLiteral: XsdLiteral =>

                                // make sure the literal is of type string
                                if (stringLiteral.datatype != OntologyConstants.Xsd.String) {
                                    throw SparqlSearchException(s"an string literal was expected, but ${stringLiteral.datatype} given")
                                }

                                val valueObjVar = createUniqueVariableFromStatement(statementPattern, "voVar") // A variable representing the value object

                                val valueObject = createStatementPatternsForValueObject(
                                    statementPattern = statementPattern,
                                    valueObject = valueObjVar,
                                    valueType = OntologyConstants.KnoraBase.TextValue
                                )

                                // match the given literal value with the integer value object's `valueHasString`
                                valueObject :+ StatementPattern.makeExplicit(subj = valueObjVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasString), obj = stringLiteral) // TODO: this is for the Where clause only (restriction)


                            case stringVar: QueryVariable =>

                                // just create the statements to query the text value object, possibly a Filter statement exists to restrict the string values
                                createStatementPatternsForValueObject(
                                    statementPattern,
                                    stringVar, // user provided variable used to represent the value object
                                    OntologyConstants.KnoraBase.TextValue
                                )

                            case other => throw SparqlSearchException(s"Not supported as an object of type string: $other")

                        }

                    }

                    case OntologyConstants.Xsd.Decimal => {

                        // the given statement pattern's object is of type xsd:decimal
                        // this means that the predicate of the statement pattern is a value property
                        // the original statement is not included because it represents the value as a literal (in Knora, it is an object)

                        // check if the statement pattern's object is a literal or a variable
                        statementPattern.obj match {

                            // create statements in order to query the decimal value object

                            case decimalLiteral: XsdLiteral =>

                                // make sure the literal is of type decimal or integer
                                if (decimalLiteral.datatype != OntologyConstants.Xsd.Decimal || decimalLiteral.datatype == OntologyConstants.Xsd.Integer) {
                                    throw SparqlSearchException(s"an decimal or integer literal was expected, but ${decimalLiteral.datatype} given")
                                }

                                val valueObjVar = createUniqueVariableFromStatement(statementPattern, "voVar") // A variable representing the value object

                                val valueObject = createStatementPatternsForValueObject(
                                    statementPattern = statementPattern,
                                    valueObject = valueObjVar,
                                    valueType = OntologyConstants.KnoraBase.DecimalValue
                                )

                                // match the given literal value with the decimal value object's `valueHasDecimal`
                                valueObject :+ StatementPattern.makeExplicit(subj = valueObjVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasDecimal), obj = decimalLiteral) // TODO: this is for the Where clause only (restriction)


                            case decimalVar: QueryVariable =>

                                // just create the statements to query the decimal value object, possibly a Filter statement exists to restrict the decimal values
                                createStatementPatternsForValueObject(
                                    statementPattern,
                                    decimalVar, // user provided variable used to represent the value object
                                    OntologyConstants.KnoraBase.DecimalValue
                                )

                            case other => throw SparqlSearchException(s"Not supported as an object of type string: $other")

                        }

                    }

                    case OntologyConstants.Xsd.Boolean => {

                        // the given statement pattern's object is of type xsd:boolean
                        // this means that the predicate of the statement pattern is a value property
                        // the original statement is not included because it represents the value as a literal (in Knora, it is an object)

                        // check if the statement pattern's object is a literal or a variable
                        statementPattern.obj match {

                            // create statements in order to query the boolean value object

                            case booleanLiteral: XsdLiteral =>

                                // make sure the literal is of type boolean
                                if (booleanLiteral.datatype != OntologyConstants.Xsd.Boolean) {
                                    throw SparqlSearchException(s"an Boolean literal was expected, but ${booleanLiteral.datatype} given")
                                }

                                val valueObjVar = createUniqueVariableFromStatement(statementPattern, "voVar") // A variable representing the value object

                                val valueObject = createStatementPatternsForValueObject(
                                    statementPattern = statementPattern,
                                    valueObject = valueObjVar,
                                    valueType = OntologyConstants.KnoraBase.BooleanValue
                                )

                                // match the given literal value with the boolean value object's `valueHasBoolean`
                                valueObject :+ StatementPattern.makeExplicit(subj = valueObjVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasBoolean), obj = booleanLiteral) // TODO: this is for the Where clause only (restriction)


                            case booleanVar: QueryVariable =>

                                // just create the statements to query the boolean value object, possibly a Filter statement exists to restrict the boolean value
                                createStatementPatternsForValueObject(
                                    statementPattern,
                                    booleanVar, // user provided variable used to represent the value object
                                    OntologyConstants.KnoraBase.BooleanValue
                                )

                            case other => throw SparqlSearchException(s"Not supported as an object of type string: $other")

                        }

                    }

                    case OntologyConstants.KnoraBase.Date => {


                        // the given statement pattern's object is of type knora-base:DateValue
                        // this means that the predicate of the statement pattern is a value property
                        // the original statement is not included because it represents the value as a literal (in Knora, it is an object)

                        // check if the statement pattern's object is a literal or a variable
                        statementPattern.obj match {

                            case dateLiteral: XsdLiteral =>

                                // make sure the literal representing a date is of type string
                                if (dateLiteral.datatype != OntologyConstants.Xsd.String) {
                                    throw SparqlSearchException(s"an string literal representing a date was expected, but ${dateLiteral.datatype} given")
                                }

                                val dateStr = InputValidation.toDate(dateLiteral.value, () => throw BadRequestException(s"${dateLiteral.value} is not a valid date string"))

                                val date: JulianDayNumberValueV1 = DateUtilV1.createJDNValueV1FromDateString(dateStr)

                                val valueObjVar = createUniqueVariableFromStatement(statementPattern, "voVar") // A variable representing the value object

                                val valueObject = createStatementPatternsForValueObject(
                                    statementPattern = statementPattern,
                                    valueObject = valueObjVar,
                                    valueType = OntologyConstants.KnoraBase.DateValue
                                )

                                val dateValueHasStartVar = createUniqueVariableFromStatement(statementPattern, "voDateValueStart")

                                val dateValueHasEndVar = createUniqueVariableFromStatement(statementPattern, "voDateValueEnd")

                                val dateValStartStatement = StatementPattern.makeExplicit(subj = valueObjVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasStartJDN), obj = dateValueHasStartVar) // TODO: this is for the Where clause only (restriction)

                                val dateValEndStatement = StatementPattern.makeExplicit(subj = valueObjVar, pred = IriRef(OntologyConstants.KnoraBase.ValueHasEndJDN), obj = dateValueHasEndVar) // TODO: this is for the Where clause only (restriction)

                                // any overlap in considered as equality
                                val leftArgFilter = CompareExpression(XsdLiteral(date.dateval1.toString, OntologyConstants.Xsd.Integer), CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO, dateValueHasEndVar)

                                val rightArgFilter = CompareExpression(XsdLiteral(date.dateval2.toString, OntologyConstants.Xsd.Integer), CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO, dateValueHasStartVar)

                                val filter = FilterPattern(AndExpression(leftArgFilter, rightArgFilter))

                                valueObject ++ Seq(dateValStartStatement, dateValEndStatement, filter)


                            case dateVar: QueryVariable =>

                                // just create the statements to query the date value object, possibly a Filter statement exists to restrict the date values
                                createStatementPatternsForValueObject(
                                    statementPattern = statementPattern,
                                    valueObject = dateVar,
                                    valueType = OntologyConstants.KnoraBase.DateValue
                                )

                            case other => throw SparqlSearchException(s"Not supported as an object of type date: $other")
                        }


                    }

                    case OntologyConstants.KnoraBase.Geom => {

                        // the given statement pattern's object is of type knora-base:GeomValue
                        // this means that the predicate of the statement pattern is a value property
                        // the original statement is not included because it represents the value as a literal (in Knora, it is an object)

                        // literals are not supported for geometry

                        statementPattern.obj match {

                            case geomVar: QueryVariable =>

                                // just create the statements to query the date value object, possibly a Filter statement exists to restrict the date values
                                createStatementPatternsForValueObject(
                                    statementPattern = statementPattern,
                                    valueObject = geomVar,
                                    valueType = OntologyConstants.KnoraBase.GeomValue
                                )

                            case other => throw SparqlSearchException(s"Not supported as an object of type geom: $other (only variable allowed for geometry, no literal)")

                        }


                    }

                    case OntologyConstants.KnoraBase.Color => {

                        // the given statement pattern's object is of type knora-base:ColorValue
                        // this means that the predicate of the statement pattern is a value property
                        // the original statement is not included because it represents the value as a literal (in Knora, it is an object)

                        // literals are not supported for color

                        statementPattern.obj match {

                            case colorVar: QueryVariable =>

                                // just create the statements to query the date value object, possibly a Filter statement exists to restrict the color values
                                createStatementPatternsForValueObject(
                                    statementPattern = statementPattern,
                                    valueObject = colorVar,
                                    valueType = OntologyConstants.KnoraBase.ColorValue
                                )

                            case other => throw SparqlSearchException(s"Not supported as an object of type color: $other (only variable allowed for color, no literal)")

                        }


                    }

                    case OntologyConstants.KnoraBase.StillImageFile => {

                        // the given statement pattern's object is of type knora-base:StillImageFileValue
                        // this means that the predicate of the statement pattern is a value property
                        // the original statement is not included because it represents the value as a literal (in Knora, it is an object)

                        // literals are not supported for StillImageFileValue

                        statementPattern.obj match {

                            case stillImageFileVar: QueryVariable =>

                                // just create the statements to query the date value object, possibly a Filter statement exists to restrict the date values
                                createStatementPatternsForValueObject(
                                    statementPattern = statementPattern,
                                    valueObject = stillImageFileVar,
                                    valueType = OntologyConstants.KnoraBase.StillImageFileValue
                                )

                            case other => throw SparqlSearchException("Not supported as an object of type color: $other (only variable allowed for color, no literal)")

                        }


                    }

                    case _ => throw NotImplementedException(s"property type $objectIri not implemented yet.")

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

                // create `TypeableEntity` (keys in `typeInspectionResult`) from the given statement's elements
                val subjTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(statementPattern.subj)

                val predTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(statementPattern.pred)

                val objTypeInfoKey: Option[TypeableEntity] = toTypeableEntityKey(statementPattern.obj)

                // check if there exists type information for the given statement's subject
                val additionalStatementsForSubj: Seq[QueryPattern] = if (subjTypeInfoKey.nonEmpty && (typeInspectionResult.typedEntities -- processedTypeInformationKeysWhereClause contains subjTypeInfoKey.get)) {
                    // process type information for the subject into additional statements

                    // add TypeableEntity (keys of `typeInspectionResult`) for subject in order to prevent duplicates
                    processedTypeInformationKeysWhereClause += subjTypeInfoKey.get

                    val nonPropTypeInfo: NonPropertyTypeInfo = typeInspectionResult.typedEntities(subjTypeInfoKey.get) match {
                        case nonPropInfo: NonPropertyTypeInfo => nonPropInfo

                        case _ => throw AssertionException(s"NonPropertyTypeInfo expected for ${subjTypeInfoKey.get}")
                    }

                    val additionalStatements = createAdditionalStatementsForNonPropertyType(
                        nonPropertyTypeInfo = nonPropTypeInfo,
                        inputEntity = statementPattern.subj
                    )

                    val existingAdditionalStatementsCreated: Seq[StatementPattern] = additionalStatementsCreatedForEntities.get(statementPattern.subj).toSeq.flatten

                    val newAdditionalStatementPatterns: Seq[StatementPattern] = additionalStatements.collect {
                        // only include StatementPattern
                        case statementP: StatementPattern => statementP
                    }

                    additionalStatementsCreatedForEntities += statementPattern.subj -> (existingAdditionalStatementsCreated ++ newAdditionalStatementPatterns)

                    additionalStatements
                } else {
                    Seq.empty[QueryPattern]
                }

                // check if there exists type information for the given statement's object
                val additionalStatementsForObj: Seq[QueryPattern] = if (objTypeInfoKey.nonEmpty && (typeInspectionResult.typedEntities -- processedTypeInformationKeysWhereClause contains objTypeInfoKey.get)) {
                    // process type information for the object into additional statements

                    // add TypeableEntity (keys of `typeInspectionResult`) for object in order to prevent duplicates
                    processedTypeInformationKeysWhereClause += objTypeInfoKey.get

                    val nonPropTypeInfo: NonPropertyTypeInfo = typeInspectionResult.typedEntities(objTypeInfoKey.get) match {
                        case nonPropInfo: NonPropertyTypeInfo => nonPropInfo

                        case _ => throw AssertionException(s"NonPropertyTypeInfo expected for ${objTypeInfoKey.get}")
                    }

                    val additionalStatements = createAdditionalStatementsForNonPropertyType(
                        nonPropertyTypeInfo = nonPropTypeInfo,
                        inputEntity = statementPattern.obj
                    )

                    val existingAdditionalStatementsCreated: Seq[StatementPattern] = additionalStatementsCreatedForEntities.get(statementPattern.obj).toSeq.flatten

                    val newAdditionalStatementPatterns: Seq[StatementPattern] = additionalStatements.collect {
                        // only include StatementPattern
                        case statementP: StatementPattern => statementP
                    }

                    additionalStatementsCreatedForEntities += statementPattern.obj -> (existingAdditionalStatementsCreated ++ newAdditionalStatementPatterns)

                    additionalStatements
                } else {
                    Seq.empty[QueryPattern]
                }

                // Add additional statements based on the whole input statement, e.g. to deal with the value object or the link value, and transform the original statement.
                val additionalStatementsForWholeStatement: Seq[QueryPattern] = if (predTypeInfoKey.nonEmpty && (typeInspectionResult.typedEntities contains predTypeInfoKey.get)) {
                    // process type information for the predicate into additional statements

                    val propTypeInfo = typeInspectionResult.typedEntities(predTypeInfoKey.get) match {
                        case propInfo: PropertyTypeInfo => propInfo

                        case _ => throw AssertionException(s"PropertyTypeInfo expected for ${predTypeInfoKey.get}")
                    }

                    val additionalStatements = convertStatementForPropertyType(
                        propertyTypeInfo = propTypeInfo,
                        statementPattern = statementPattern
                    )

                    val existingAdditionalStatementsCreated: Seq[StatementPattern] = convertedStatementsCreatedForWholeStatements.get(statementPattern).toSeq.flatten

                    val newAdditionalStatementPatterns: Seq[StatementPattern] = additionalStatements.collect {
                        // only include StatementPattern

                        // TODO: filter out those statements mentioning the originally given predicate (property) with inference turned on
                        // this is necessary to return the explicit property to the user
                        // the originally given property will only be needed in the Where clause and get all subproperties too
                        case statementP: StatementPattern /*if statementP.includeInConstructClause*/ => statementP
                    }

                    convertedStatementsCreatedForWholeStatements += statementPattern -> (existingAdditionalStatementsCreated ++ newAdditionalStatementPatterns)

                    additionalStatements
                } else {
                    // no type information given and thus no further processing needed, just return the originally given statement (e.g., rdf:type)
                    Seq(statementPattern)
                }


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

            case class TransformedFilterExpression(expression: Expression, additionalStatements: Seq[StatementPattern] = Seq.empty[StatementPattern])

            def transformFilterExpression(filterExpression: Expression): TransformedFilterExpression = {

                filterExpression match {

                    case filterCompare: CompareExpression =>

                        // left argument of a CompareExpression is expected to be a QueryVariable

                        val queryVar: QueryVariable = filterCompare.leftArg match {

                            case queryVar: QueryVariable => queryVar

                            case other => throw SparqlSearchException(s"Left argument of a Filter CompareExpression is expected to be a QueryVariable, but $other is given")
                        }

                        val queryVarTypeInfoKey = toTypeableEntityKey(queryVar)

                        // get information about the queryVar's type
                        if (queryVarTypeInfoKey.nonEmpty && (typeInspectionResult.typedEntities contains queryVarTypeInfoKey.get)) {

                            val typeInfo: SparqlEntityTypeInfo = typeInspectionResult.typedEntities(queryVarTypeInfoKey.get)

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

                                            val intValHasInteger = createUniqueVariableNameFromEntityAndProperty(queryVar, OntologyConstants.KnoraBase.ValueHasInteger)

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
                        val filterExpressionLeft = transformFilterExpression(filterOr.leftArg)
                        val filterExpressionRight = transformFilterExpression(filterOr.rightArg)

                        TransformedFilterExpression(
                            OrExpression(filterExpressionLeft.expression, filterExpressionRight.expression),
                            filterExpressionLeft.additionalStatements ++ filterExpressionRight.additionalStatements
                        )


                    case filterAnd: AndExpression =>
                        val filterExpressionLeft = transformFilterExpression(filterAnd.leftArg)
                        val filterExpressionRight = transformFilterExpression(filterAnd.rightArg)

                        TransformedFilterExpression(
                            AndExpression(filterExpressionLeft.expression, filterExpressionRight.expression),
                            filterExpressionLeft.additionalStatements ++ filterExpressionRight.additionalStatements
                        )

                    case other => throw NotImplementedException(s"$other not supported as FilterExpression")
                }


            }

            def transformFilter(filterPattern: FilterPattern): Seq[QueryPattern] = {

                val filterExpression: TransformedFilterExpression = transformFilterExpression(filterPattern.expression)

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

            // TODO: Run the prequery to get the IRIs of the current page of search results.


            // TODO: include those IRIs in a FILTER in the CONSTRUCT clause.

            // Convert the preprocessed query to a non-triplestore-specific query.

            nonTriplestoreSpecificQuery: ConstructQuery = QueryTraverser.transformConstructToConstruct(
                inputQuery = preprocessedQuery,
                transformer = new NonTriplestoreSpecificConstructToConstructTransformer(typeInspectionResult)
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

            triplestoreSpecificPrequery = QueryTraverser.transformSelectToSelect(
                inputQuery = nonTriplestoreSpecficPrequery,
                transformer = triplestoreSpecificQueryPatternTransformerSelect
            )

            _ = println(triplestoreSpecificPrequery.toSparql)

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
                inputQuery = nonTriplestoreSpecificQuery,
                transformer = triplestoreSpecificQueryPatternTransformerConstruct
            )

            // Convert the result to a SPARQL string and send it to the triplestore.

            /*
                        statementsInWhereClause = triplestoreSpecificQuery.whereClause.patterns.collect {
                            case statementPattern: StatementPattern => statementPattern
                        }

                        nonStatementsInWhereClause = triplestoreSpecificQuery.whereClause.patterns.filter {
                            case statementPattern: StatementPattern => false
                            case _ => true
                        }

                        statementsInConstructClause = triplestoreSpecificQuery.constructClause.statements.map(_)


                        statementsInWhereButNotInConstruct = statementsInWhereClause.diff(statementsInConstructClause)
                        statementsInConstructButNotInWhere = statementsInConstructClause.diff(statementsInWhereClause)

                        _ = println(s"statementsInWhereButNotInConstruct: $statementsInWhereButNotInConstruct")
                        _ = println(s"statementsInConstructButNotInWhere: $statementsInConstructButNotInWhere")
                        _ = println(s"nonStatementsInWhereClause: $nonStatementsInWhereClause")
            */

            triplestoreSpecificSparql: String = triplestoreSpecificQuery.toSparql

            // _ = println(triplestoreSpecificQuery.toSparql)

            searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(triplestoreSpecificSparql)).mapTo[SparqlConstructResponse]

            // separate resources and value objects
            queryResultsSeparated: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = ConstructResponseUtilV2.splitResourcesAndValueRdfData(constructQueryResults = searchResponse, userProfile = userProfile)

        // TODO: pass the ORDER BY criterion, if any
        } yield ReadResourcesSequenceV2(numberOfResources = queryResultsSeparated.size, resources = ConstructResponseUtilV2.createSearchResponse(queryResultsSeparated))
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