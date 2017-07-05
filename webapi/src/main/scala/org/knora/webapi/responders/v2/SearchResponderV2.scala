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

import akka.pattern._
import org.knora.webapi.messages.store.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.search.v2.{ApiV2Schema, _}
import org.knora.webapi.util.{ConstructResponseUtilV2, InputValidation}
import org.knora.webapi._


import scala.concurrent.Future

class SearchResponderV2 extends Responder {

    def receive = {
        case FulltextSearchGetRequestV2(searchValue, userProfile) => future2Message(sender(), fulltextSearchV2(searchValue, userProfile), log)
        case ExtendedSearchGetRequestV2(query, userProfile) => future2Message(sender(), extendedSearchV2(simpleConstructQuery = query, userProfile = userProfile), log)
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
      * @param simpleConstructQuery Sparql construct query provided by the client.
      * @param userProfile          the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def extendedSearchV2(simpleConstructQuery: SimpleConstructQuery, apiSchema: ApiV2Schema.Value = ApiV2Schema.SIMPLE, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        /**
          * Converts a [[FilterExpression]] to an [[ExtendedSearchFilterExpression]].
          *
          * @param filterExpression a filter expression provided by [[SearchParserV2]].
          * @return a [[ExtendedSearchFilterExpression]].
          */
        def convertFilterExpressionToExtendedSearchFilterExpression(filterExpression: FilterExpression): ExtendedSearchFilterExpression = {
            filterExpression match {
                case entity: Entity => convertSearchParserEntityToExtendedSearchEntity(entity)
                case compareExpr: CompareExpression => ExtendedSearchCompareExpression(leftArg = convertFilterExpressionToExtendedSearchFilterExpression(compareExpr.leftArg), operator = compareExpr.operator, rightArg = convertFilterExpressionToExtendedSearchFilterExpression(compareExpr.rightArg))
                case andExpr: AndExpression => ExtendedSearchAndExpression(leftArg = convertFilterExpressionToExtendedSearchFilterExpression(andExpr.leftArg), rightArg = convertFilterExpressionToExtendedSearchFilterExpression(andExpr.rightArg))
                case orExpr: OrExpression => ExtendedSearchOrExpression(leftArg = convertFilterExpressionToExtendedSearchFilterExpression(orExpr.leftArg), rightArg = convertFilterExpressionToExtendedSearchFilterExpression(orExpr.rightArg))
            }
        }

        /**
          * Converts an [[Entity]] to an [[ExtendedSearchEntity]].
          *
          * @param entity an entity provided by [[SearchParserV2]].
          * @return a [[ExtendedSearchEntity]].
          */
        def convertSearchParserEntityToExtendedSearchEntity(entity: Entity): ExtendedSearchEntity = {
            // convert external Iris to internal Iris if needed

            entity match {
                case iriRef: IriRef => // if an Iri is an external knora-api entity (with value object or simple), convert it to an internal Iri
                    if (InputValidation.isKnoraApiEntityIri(iriRef.iri)) {
                        ExtendedSearchInternalEntityIri(InputValidation.externalIriToInternalIri(iriRef.iri, () => throw BadRequestException(s"${iriRef.iri} is not a valid external knora-api entity Iri")))
                    } else {
                        ExtendedSearchIri(InputValidation.toIri(iriRef.iri, () => throw BadRequestException(s"$iriRef is not a valid IRI")))
                    }
                case queryVar: QueryVariable => ExtendedSearchVar(queryVar.variableName)
                case literal: XsdLiteral => ExtendedSearchXsdLiteral(value = literal.value, datatype = literal.datatype)
            }
        }

        /**
          * Converts a [[StatementPattern]] provided by [[SearchParserV2]] to an [[ExtendedSearchStatementPattern]].
          *
          * @param statementPattern a statement provided by SearchParserV2.
          * @param disableInference indicates if inference should be disabled for this statement.
          * @return a [[ExtendedSearchStatementPattern]].
          */
        def convertSearchParserStatementPatternToExtendedSearchStatement(statementPattern: StatementPattern, disableInference: Boolean): ExtendedSearchStatementPattern = {

            val subj = convertSearchParserEntityToExtendedSearchEntity(statementPattern.subj)
            val pred = convertSearchParserEntityToExtendedSearchEntity(statementPattern.pred)
            val obj = convertSearchParserEntityToExtendedSearchEntity(statementPattern.obj)

            ExtendedSearchStatementPattern(
                subj = subj,
                pred = pred,
                obj = obj,
                disableInference = disableInference || (pred match { // disable inference if `disableInference` is set to true or if the statement's predicate is a variable.
                    case variable: ExtendedSearchVar => true // disable inference to get the actual IRI for the predicate and not an inferred information
                    case _ => false
                })
            )
        }

        /**
          *
          * Converts a [[QueryPattern]] provided by [[SearchParserV2]] to an [[ExtendedSearchQueryPattern]].
          *
          * @param patterns a query pattern provided by SearchParserV2.
          * @return a [[ExtendedSearchQueryPattern]].
          */
        def convertSearchParserQueryPatternsToExtendedSearchPatterns(patterns: Seq[QueryPattern]): Seq[ExtendedSearchQueryPattern] = {
            // convert the statement patterns to a sequence of `ExtendedSearchStatementPattern`.
            patterns.map {
                (queryP: QueryPattern) =>
                    queryP match {
                        case statementPattern: StatementPattern =>
                            convertSearchParserStatementPatternToExtendedSearchStatement(statementPattern = statementPattern, disableInference = false) // use inference (will be disabled only for statements whose predicate is a variable).

                        case optionalPattern: OptionalPattern => ExtendedSearchOptionalPattern(convertSearchParserQueryPatternsToExtendedSearchPatterns(optionalPattern.patterns))

                        case unionPattern: UnionPattern =>
                            val blocksWithoutAnnotations = unionPattern.blocks.map {
                                patterns: Seq[QueryPattern] => convertSearchParserQueryPatternsToExtendedSearchPatterns(patterns)
                            }

                            ExtendedSearchUnionPattern(blocksWithoutAnnotations)

                        case filterPattern: FilterPattern => ExtendedSearchFilterPattern(convertFilterExpressionToExtendedSearchFilterExpression(filterPattern.expression))
                    }
            }
        }

        /**
          * Converts a sequence of [[StatementPattern]] representing the Construct clause of a query to a sequence of [[ExtendedSearchStatementPattern]].
          *
          * @param patterns a sequence of statement patterns provided by [[SearchParserV2]].
          * @return a sequence of [[ExtendedSearchStatementPattern]].
          */
        def convertSearchParserConstructPatternsToExtendedSearchPatterns(patterns: Seq[StatementPattern]): Seq[ExtendedSearchStatementPattern] = {
            patterns.map(statementPattern => convertSearchParserStatementPatternToExtendedSearchStatement(statementPattern = statementPattern, disableInference = true))
        }

        val typeInspector = new ExplicitTypeInspectorV2(apiSchema)
        val whereClauseWithoutAnnotations: SimpleWhereClause = typeInspector.removeTypeAnnotations(simpleConstructQuery.whereClause)
        val typeInspectionResultWhere: TypeInspectionResultV2 = typeInspector.inspectTypes(simpleConstructQuery.whereClause)

        val extendedSearchConstructClauseStatementPatterns: Seq[ExtendedSearchStatementPattern] = convertSearchParserConstructPatternsToExtendedSearchPatterns(simpleConstructQuery.constructClause.statements)
        val extendedSearchWhereClausePatternsWithOriginalFilters: Seq[ExtendedSearchQueryPattern] = convertSearchParserQueryPatternsToExtendedSearchPatterns(whereClauseWithoutAnnotations.patterns)

        /**
          * Creates additional statements based on a non property type Iri.
          *
          * @param typeIriExternal  the non property type Iri.
          * @param statementPattern the statement to be processed.
          * @param subject          the entity that is the subject in the additional statement to be generated.
          * @param typeInfoKeysProcessed a Set of keys that indicates which type info entries already habe been processed.
          * @param index            the index to be used to create variables in Sparql.
          * @return a sequence of [[ExtendedSearchStatementPattern]].
          */
        def createAdditionalStatementsForNonPropertyType(typeIriExternal: IRI, statementPattern: ExtendedSearchStatementPattern, subject: ExtendedSearchEntity, typeInfoKeysProcessed: Set[TypeableEntityV2], index: Int): AdditionalStatements = {
            val typeIriInternal = InputValidation.externalIriToInternalIri(typeIriExternal, () => throw BadRequestException(s"${typeIriExternal} is not a valid external knora-api entity Iri"))

            // TODO: decide whether the given statement pattern has to be included in the return value

            if (typeIriInternal == OntologyConstants.KnoraBase.Resource) {
                AdditionalStatements(additionalStatements = Vector(
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchIri(OntologyConstants.KnoraBase.Resource), false),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchIri(OntologyConstants.Rdfs.Label), obj = ExtendedSearchVar("resourceLabel" + index), true),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchVar("resourceType" + index), true),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.AttachedToUser), obj = ExtendedSearchVar("resourceCreator" + index), true),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasPermissions), obj = ExtendedSearchVar("resourcePermissions" + index), true),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.AttachedToProject), obj = ExtendedSearchVar("resourceProject" + index), true)
                ), filterKeysProcessed = typeInfoKeysProcessed)
            } else {
                // TODO: handle more cases here

                AdditionalStatements()
            }
        }

        /**
          * Creates additional statements based on a property type Iri.
          *
          * @param typeIriExternal  the property type Iri.
          * @param statementPattern the statement to be processed.
          * @param typeInfoKeysProcessed a Set of keys that indicates which type info entries already habe been processed.
          * @param index            the index to be used to create variables in Sparql.
          * @return a sequence of [[ExtendedSearchStatementPattern]].
          */
        def createAdditionalStatementsForPropertyType(typeIriExternal: IRI, statementPattern: ExtendedSearchStatementPattern, typeInfoKeysProcessed: Set[TypeableEntityV2], index: Int): AdditionalStatements = {

            val objectIri = if (InputValidation.isKnoraApiEntityIri(typeIriExternal)) {
                InputValidation.externalIriToInternalIri(typeIriExternal, () => throw BadRequestException(s"${typeIriExternal} is not a valid external knora-api entity Iri"))
            } else {
                typeIriExternal
            }

            objectIri match {
                case OntologyConstants.KnoraBase.Resource =>
                    AdditionalStatements(additionalStatements = Vector(
                        ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = ExtendedSearchVar("linkValueObj" + index), false),
                        ExtendedSearchStatementPattern(subj = ExtendedSearchVar("linkValueObj" + index), pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                        ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchVar("linkValueProp" + index), obj = ExtendedSearchVar("linkValueObj" + index), true),
                        ExtendedSearchStatementPattern(subj = ExtendedSearchVar("linkValueObj" + index), pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.LinkValue), true),
                        ExtendedSearchStatementPattern(subj = ExtendedSearchVar("linkValueObj" + index), pred = ExtendedSearchIri(OntologyConstants.Rdf.Subject), obj = statementPattern.subj, true),
                        ExtendedSearchStatementPattern(subj = ExtendedSearchVar("linkValueObj" + index), pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Object), obj = statementPattern.obj, true),
                        ExtendedSearchStatementPattern(subj = ExtendedSearchVar("linkValueObj" + index), pred = ExtendedSearchVar("linkValueObjProp" + index), obj = ExtendedSearchVar("linkValueObjVal" + index), true)
                    ), filterKeysProcessed = typeInfoKeysProcessed)
                case OntologyConstants.Xsd.String =>

                    if (apiSchema == ApiV2Schema.SIMPLE) {
                        // do not include the given statement pattern in the answer because a direct statement from the resource to a string literal (simplified) has to be translated to a value object (extra level).

                        statementPattern.obj match {

                            case stringLiteral: ExtendedSearchXsdLiteral =>
                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = ExtendedSearchVar("stringValueObj" + index), false),
                                    ExtendedSearchStatementPattern(subj = ExtendedSearchVar("stringValueObj" + index), pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = ExtendedSearchVar("stringValueObj" + index), false),
                                    ExtendedSearchStatementPattern(subj = ExtendedSearchVar("stringValueObj" + index), pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.TextValue), true),
                                    ExtendedSearchStatementPattern(subj = ExtendedSearchVar("stringValueObj" + index), pred = ExtendedSearchVar("stringValueObjProp" + index), obj = ExtendedSearchVar("stringValueObjVal" + index), true),
                                    ExtendedSearchStatementPattern(subj = ExtendedSearchVar("stringValueObj" + index), pred = ExtendedSearchIri(OntologyConstants.KnoraBase.ValueHasString), obj = statementPattern.obj, true)
                                ), filterKeysProcessed = typeInfoKeysProcessed)

                            case stringVar: ExtendedSearchVar =>
                                AdditionalStatements(additionalStatements = Vector(
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = stringVar, false),
                                    ExtendedSearchStatementPattern(subj = stringVar, pred = ExtendedSearchIri(OntologyConstants.KnoraBase.IsDeleted), obj = ExtendedSearchXsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean), true),
                                    ExtendedSearchStatementPattern(subj = statementPattern.subj, pred = statementPattern.pred, obj = stringVar, false),
                                    ExtendedSearchStatementPattern(subj = stringVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.TextValue), true),
                                    ExtendedSearchStatementPattern(subj = stringVar, pred = ExtendedSearchVar("stringValueObjProp" + index), obj = ExtendedSearchVar("stringValueObjVal" + index), true)
                                ), filterKeysProcessed = typeInfoKeysProcessed)

                            case _ => AdditionalStatements()

                        }

                    } else {
                        throw NotImplementedException(s"Extended search not implemented for schema $apiSchema")
                    }


                case OntologyConstants.Xsd.Boolean =>
                    AdditionalStatements()

                case OntologyConstants.Xsd.Integer =>
                    AdditionalStatements()

                case OntologyConstants.Xsd.Decimal =>
                    AdditionalStatements()

                case OntologyConstants.KnoraApiV2Simplified.Date =>
                    AdditionalStatements()

                case other =>
                    AdditionalStatements()
            }

        }

        /**
          * Represents the originally given statement in the query provided by the user and statements that were additionally cretaed based on the given type annotations.
          *
          * @param additionalStatements statements created based on the given type annotations.
          * @param filterKeysProcessed a Set of keys that indicates which type info entries already habe been processed.
          */
        case class AdditionalStatements(additionalStatements: Vector[ExtendedSearchStatementPattern] = Vector.empty[ExtendedSearchStatementPattern], filterKeysProcessed: Set[TypeableEntityV2] = Set.empty[TypeableEntityV2])

        /**
          *
          * @param originalStatement the statement originally given by the user. Since some statements have to be converted (e.g. direct statements from resources to literals in the simplified Api schema), original statements might not be returned.
          * @param additionalStatements statements created based on the given type annotations.
          */
        case class ConvertedStatement(originalStatement: Option[ExtendedSearchStatementPattern], additionalStatements: AdditionalStatements)

        /**
          * Based on the given type annotations, convert the given statement.
          *
          * @param statementP the given statement.
          * @param index the current index (used to create unique variable names).
          * @param filterKeysProcessedInStatements a Set of keys that indicates which type info entries already habe been processed.
          * @return a sequence of [[AdditionalStatements]].
          */
        def addTypeInformationStatementsToStatement(statementP: ExtendedSearchStatementPattern, index: Int, filterKeysProcessedInStatements: Set[TypeableEntityV2]): ConvertedStatement = {

            // check if subject is contained in the type info
            val additionalStatementsForSubj: AdditionalStatements = statementP.subj match {
                case variableSubj: ExtendedSearchVar =>
                    val key = TypeableVariableV2(variableSubj.variableName)

                    if (typeInspectionResultWhere.typedEntities -- filterKeysProcessedInStatements contains key) {

                        val additionalStatements: AdditionalStatements = typeInspectionResultWhere.typedEntities(key) match {
                            case nonPropTypeInfo: NonPropertyTypeInfoV2 =>
                                createAdditionalStatementsForNonPropertyType(nonPropTypeInfo.typeIri, statementP, statementP.subj, filterKeysProcessedInStatements + key, index)
                            case propTypeInfo: PropertyTypeInfoV2 =>
                                AdditionalStatements()
                        }

                        additionalStatements


                    } else {
                        AdditionalStatements()
                    }


                case iriSubj: ExtendedSearchInternalEntityIri =>
                    val key = TypeableIriV2(iriSubj.iri)

                    AdditionalStatements()

                case other =>
                    AdditionalStatements()
            }


            val additionalStatementsForPred: AdditionalStatements = statementP.pred match {
                case variablePred: ExtendedSearchVar =>
                    val key = TypeableVariableV2(variablePred.variableName)

                    if (typeInspectionResultWhere.typedEntities -- filterKeysProcessedInStatements contains key) {

                        val additionalStatements: AdditionalStatements = typeInspectionResultWhere.typedEntities(key) match {
                            case nonPropTypeInfo: NonPropertyTypeInfoV2 =>
                                AdditionalStatements()

                            case propTypeInfo: PropertyTypeInfoV2 =>
                                createAdditionalStatementsForPropertyType(propTypeInfo.objectTypeIri, statementP, filterKeysProcessedInStatements + key, index)
                        }

                        additionalStatements

                    } else {
                        AdditionalStatements()
                    }

                case iriPred: ExtendedSearchInternalEntityIri =>

                    val key = if (apiSchema == ApiV2Schema.SIMPLE) {
                        // convert this Iri to knora-api simple since the type inspector uses knora-api simple Iris
                        TypeableIriV2(InputValidation.internalEntityIriToSimpleApiV2EntityIri(iriPred.iri, () => throw AssertionException(s"${iriPred.iri} could not be converted back to knora-api simple format")))
                    } else {
                        throw NotImplementedException("The extended search for knora-api with value object has not been implemented yet")
                    }

                    if (typeInspectionResultWhere.typedEntities -- filterKeysProcessedInStatements contains key) {

                        val additionalStatements: AdditionalStatements = typeInspectionResultWhere.typedEntities(key) match {
                            case nonPropTypeInfo: NonPropertyTypeInfoV2 =>
                                AdditionalStatements()

                            case propTypeInfo: PropertyTypeInfoV2 =>
                                createAdditionalStatementsForPropertyType(propTypeInfo.objectTypeIri, statementP, filterKeysProcessedInStatements + key, index)
                        }

                        additionalStatements

                    } else {
                        AdditionalStatements()
                    }

                case other => AdditionalStatements()
            }

            val additionalStatementsForObj: AdditionalStatements = statementP.obj match {
                case variableObj: ExtendedSearchVar =>
                    AdditionalStatements()

                case internalEntityIriObj: ExtendedSearchInternalEntityIri =>
                    AdditionalStatements()

                case iriObj: ExtendedSearchIri =>
                    val key = TypeableIriV2(iriObj.iri)

                    if (typeInspectionResultWhere.typedEntities -- filterKeysProcessedInStatements contains key) {

                        val additionalStatements: AdditionalStatements = typeInspectionResultWhere.typedEntities(key) match {
                            case nonPropTypeInfo: NonPropertyTypeInfoV2 =>
                                createAdditionalStatementsForNonPropertyType(nonPropTypeInfo.typeIri, statementP, statementP.obj, filterKeysProcessedInStatements + key, index)
                            case propTypeInfo: PropertyTypeInfoV2 =>
                                AdditionalStatements()

                        }

                        additionalStatements

                    } else {
                        AdditionalStatements()
                    }

                case other => AdditionalStatements()
            }

            val additionalStatementsAll: AdditionalStatements = Vector(additionalStatementsForSubj, additionalStatementsForPred, additionalStatementsForObj).foldLeft(AdditionalStatements()) {
                case (acc: AdditionalStatements, addStatements: AdditionalStatements) =>

                    AdditionalStatements(additionalStatements = acc.additionalStatements ++ addStatements.additionalStatements, filterKeysProcessed = acc.filterKeysProcessed ++ addStatements.filterKeysProcessed)

            }

            // decide whether the given statement has to be included
            if (apiSchema == ApiV2Schema.SIMPLE) {

                // if pred is a valueProp, do not return the original statement
                // it had to be converted to comply with knora's value object structure

                statementP.pred match {
                    case iriPred: ExtendedSearchInternalEntityIri =>

                        val key = if (apiSchema == ApiV2Schema.SIMPLE) {
                            // convert this Iri to knora-api simple since the type inspector uses knora-api simple Iris
                            TypeableIriV2(InputValidation.internalEntityIriToSimpleApiV2EntityIri(iriPred.iri, () => throw AssertionException(s"${iriPred.iri} could not be converted back to knora-api simple format")))
                        } else {
                            throw NotImplementedException("The extended search for knora-api with value object has not been implemented yet")
                        }

                        typeInspectionResultWhere.typedEntities.get(key) match {
                            case Some(propTypeInfo: PropertyTypeInfoV2) if propTypeInfo.objectTypeIri != OntologyConstants.KnoraBase.Resource =>
                                // value prop -> additonal statements have been created to comply with knora's value object structure
                                ConvertedStatement(originalStatement = None, additionalStatements = additionalStatementsAll)
                            case _ =>
                                // include original statement
                                ConvertedStatement(originalStatement = Some(statementP), additionalStatements = additionalStatementsAll)
                        }

                    case _ => // include original statement
                        ConvertedStatement(originalStatement = Some(statementP), additionalStatements = additionalStatementsAll)

                }

            } else {
                throw NotImplementedException(s"Extended search not implemented for schema $apiSchema")
            }

        }

        /**
          * Represents original query patterns provided by the user a additional statements created on the bases of the given type annotations.
          *
          * @param originalPatterns the patterns originally provided by the user.
          * @param additionalPatterns additional statements created on the bases of the given type annotations.
          * @param typeInfoKeysProcessedInStatements a Set of keys that indicates which type info entries already habe been processed.
          */
        case class ConvertedQueryPatterns(originalPatterns: Vector[ExtendedSearchQueryPattern], additionalPatterns: Vector[ExtendedSearchStatementPattern], typeInfoKeysProcessedInStatements: Set[TypeableEntityV2])

        def convertQueryPatterns(patterns: Seq[ExtendedSearchQueryPattern]): ConvertedQueryPatterns = {
            patterns.zipWithIndex.foldLeft(ConvertedQueryPatterns(originalPatterns = Vector.empty[ExtendedSearchQueryPattern], additionalPatterns = Vector.empty[ExtendedSearchStatementPattern], typeInfoKeysProcessedInStatements = Set.empty[TypeableEntityV2])) {

                case (acc: ConvertedQueryPatterns, (pattern: ExtendedSearchQueryPattern, index: Int)) =>

                    pattern match {
                        case statementP: ExtendedSearchStatementPattern =>

                            val convertedStatement: ConvertedStatement = addTypeInformationStatementsToStatement(statementP, index, acc.typeInfoKeysProcessedInStatements)

                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ convertedStatement.originalStatement, additionalPatterns = acc.additionalPatterns ++ convertedStatement.additionalStatements.additionalStatements, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements ++ convertedStatement.additionalStatements.filterKeysProcessed)


                        case optionalP: ExtendedSearchOptionalPattern =>
                            val optionalPatterns = Seq(ExtendedSearchOptionalPattern(convertQueryPatterns(optionalP.patterns).originalPatterns))

                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ optionalPatterns, additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                        case unionP: ExtendedSearchUnionPattern =>
                            val blocks = unionP.blocks.map {
                                blockPatterns: Seq[ExtendedSearchQueryPattern] => convertQueryPatterns(blockPatterns).originalPatterns
                            }

                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(ExtendedSearchUnionPattern(blocks)), additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                        case filterP: ExtendedSearchFilterPattern =>
                            // TODO: transform filter, possibly adding more statements (text, date comparison etc.)
                            
                            filterP.expression match {
                                case filterCompare: ExtendedSearchCompareExpression =>


                                    filterCompare.leftArg match {
                                        case searchVar: ExtendedSearchVar =>

                                            val objectType: SparqlEntityTypeInfoV2 = typeInspectionResultWhere.typedEntities(TypeableVariableV2(searchVar.variableName))


                                            objectType match {
                                                case nonPropTypeInfo: NonPropertyTypeInfoV2 =>

                                                    nonPropTypeInfo.typeIri match {
                                                        case OntologyConstants.Xsd.String =>
                                                            val statement = ExtendedSearchStatementPattern(subj = searchVar, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.ValueHasString), obj = ExtendedSearchVar("stringVar" + index), true)
                                                            val filterPattern = ExtendedSearchFilterPattern(ExtendedSearchCompareExpression(leftArg = ExtendedSearchVar("stringVar" + index), operator = "=", rightArg = filterCompare.rightArg))

                                                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns :+ filterPattern, additionalPatterns = acc.additionalPatterns :+ statement, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)
                                                        case _ =>
                                                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(filterP), additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)
                                                    }


                                                case _ =>
                                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(filterP), additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)
                                            }


                                        case searchIri: ExtendedSearchInternalEntityIri =>
                                            println(filterCompare)
                                            println(searchIri)
                                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(filterP), additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)

                                        case _ =>
                                            ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(filterP), additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)
                                    }


                                case filterOr: ExtendedSearchOrExpression =>
                                    //println(filterOr)
                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(filterP), additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)
                                case filterAnd: ExtendedSearchAndExpression =>
                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(filterP), additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)
                                case _ =>
                                    ConvertedQueryPatterns(originalPatterns = acc.originalPatterns ++ Seq(filterP), additionalPatterns = acc.additionalPatterns, typeInfoKeysProcessedInStatements = acc.typeInfoKeysProcessedInStatements)
                            }

                    }


            }
        }

        // convert where statements (handle FILTER expressions)
        val extendedSearchWhereClausePatternsConverted: ConvertedQueryPatterns = convertQueryPatterns(extendedSearchWhereClausePatternsWithOriginalFilters)

        val constructQuery = ExtendedSearchQuery(
            constructClause = extendedSearchConstructClauseStatementPatterns.toVector ++ extendedSearchWhereClausePatternsConverted.additionalPatterns,
            whereClause = extendedSearchWhereClausePatternsConverted.originalPatterns ++ extendedSearchWhereClausePatternsConverted.additionalPatterns
        )

        for {

            searchSparql <- Future(queries.sparql.v2.txt.searchExtended(
                triplestore = settings.triplestoreType,
                query = constructQuery
            ).toString())

            _ = println(searchSparql)


            searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(searchSparql)).mapTo[SparqlConstructResponse]


            // separate resources and value objects
            queryResultsSeparated: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = ConstructResponseUtilV2.splitResourcesAndValueRdfData(constructQueryResults = searchResponse, userProfile = userProfile)

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