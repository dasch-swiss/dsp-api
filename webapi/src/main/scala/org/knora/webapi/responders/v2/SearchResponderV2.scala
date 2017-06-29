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
import org.knora.webapi.util.search.v2._
import org.knora.webapi.util.{ConstructResponseUtilV2, InputValidation}
import org.knora.webapi.{BadRequestException, IRI, OntologyConstants}

import scala.concurrent.Future

class SearchResponderV2 extends Responder {

    def receive = {
        case FulltextSearchGetRequestV2(searchValue, userProfile) => future2Message(sender(), fulltextSearchV2(searchValue, userProfile), log)
        case ExtendedSearchGetRequestV2(query, userProfile) => future2Message(sender(), extendedSearchV2(query, userProfile), log)
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
      * Performs an extended search.
      *
      * @param simpleConstructQuery Sparql construct query provided by the client.
      * @param userProfile          the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def extendedSearchV2(simpleConstructQuery: SimpleConstructQuery, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

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

        def convertSearchParserConstructPatternsToExtendedSearchPatterns(patterns: Seq[StatementPattern]): Seq[ExtendedSearchStatementPattern] = {
            patterns.map(statementPattern => convertSearchParserStatementPatternToExtendedSearchStatement(statementPattern = statementPattern, disableInference = true))
        }

        val typeInspector = new ExplicitTypeInspectorV2(ApiV2Schema.SIMPLE)
        val whereClauseWithoutAnnotations: SimpleWhereClause = typeInspector.removeTypeAnnotations(simpleConstructQuery.whereClause)
        var typeInspectionResultWhere: TypeInspectionResultV2 = typeInspector.inspectTypes(simpleConstructQuery.whereClause)

        val extendedSearchConstructClauseStatementPatterns: Seq[ExtendedSearchStatementPattern] = convertSearchParserConstructPatternsToExtendedSearchPatterns(simpleConstructQuery.constructClause.statements)
        val extendedSearchWhereClausePatternsWithOriginalFilters: Seq[ExtendedSearchQueryPattern] = convertSearchParserQueryPatternsToExtendedSearchPatterns(whereClauseWithoutAnnotations.patterns)

        /**
          * Creates additional statements based on a non property type Iri.
          *
          * @param typeIriExternal the non property type Iri.
          * @param subject the subject of the statement to be created.
          * @param index the index to be used to create variables in Sparql.
          * @return a sequence of [[ExtendedSearchStatementPattern]].
          */
        def createAdditionalStatementsForNonPropertyType(typeIriExternal: IRI, subject: ExtendedSearchEntity, index: Int): Vector[ExtendedSearchStatementPattern] = {
            val typeIriInternal = InputValidation.externalIriToInternalIri(typeIriExternal, () => throw BadRequestException(s"${typeIriExternal} is not a valid external knora-api entity Iri"))

            if (typeIriInternal == OntologyConstants.KnoraBase.Resource) {
                Vector(
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchIri(OntologyConstants.KnoraBase.Resource), false),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchIri(OntologyConstants.Rdfs.Label), obj = ExtendedSearchVar("resourceLabel" + index), true),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchVar("resourceType" + index), true),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.AttachedToUser), obj = ExtendedSearchVar("resourceCreator" + index), true),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasPermissions), obj = ExtendedSearchVar("resourcePermissions" + index), true),
                    ExtendedSearchStatementPattern(subj = subject, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.AttachedToProject), obj = ExtendedSearchVar("resourceProject" + index), true)
                )
            } else {
                // TODO: handle more cases here

                Vector.empty[ExtendedSearchStatementPattern]
            }
        }

        /**
          * Creates additional statements based on a property type Iri.
          *
          * @param typeIriExternal the property type Iri.
          * @param subjectInStatement the subject of the statement to be created.
          * @param objectInStatement the object the property points to (resource instance in case of a linking property or a value in case of a value property).
          * @param index the index to be used to create variables in Sparql.
          * @return a sequence of [[ExtendedSearchStatementPattern]].
          */
        def createAdditionalStatementsForPropertyType(typeIriExternal: IRI, subjectInStatement: ExtendedSearchEntity, objectInStatement: ExtendedSearchEntity, index: Int): Vector[ExtendedSearchStatementPattern] = {
            val objectIri = InputValidation.externalIriToInternalIri(typeIriExternal, () => throw BadRequestException(s"${typeIriExternal} is not a valid external knora-api entity Iri"))

            if (objectIri == OntologyConstants.KnoraBase.Resource) {
                Vector(
                    ExtendedSearchStatementPattern(subj = subjectInStatement, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = ExtendedSearchVar("linkValueObj" + index), false),
                    ExtendedSearchStatementPattern(subj = subjectInStatement, pred = ExtendedSearchVar("linkValueProp" + index), obj = ExtendedSearchVar("linkValueObj" + index), true),
                    ExtendedSearchStatementPattern(subj = ExtendedSearchVar("linkValueObj" + index), pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.LinkValue), true),
                    ExtendedSearchStatementPattern(subj = ExtendedSearchVar("linkValueObj" + index), pred = ExtendedSearchIri(OntologyConstants.Rdf.Subject), obj = subjectInStatement, true),
                    ExtendedSearchStatementPattern(subj = ExtendedSearchVar("linkValueObj" + index), pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Object), obj = objectInStatement, true),
                    ExtendedSearchStatementPattern(subj = ExtendedSearchVar("linkValueObj" + index), pred = ExtendedSearchVar("linkValueObjProp" + index), obj = ExtendedSearchVar("linkValueObjVal" + index), true)
                )
            } else {
                // TODO: handle more cases here

                Vector.empty[ExtendedSearchStatementPattern]
            }
        }

        def addAdditionalStatementsForStatement(statementP: ExtendedSearchStatementPattern, index: Int): Vector[ExtendedSearchStatementPattern] = {

            // check if subject is contained in the type info
            val additionalStatementsForSubj: Vector[ExtendedSearchStatementPattern] = statementP.subj match {
                case variableSubj: ExtendedSearchVar =>
                    val key = TypeableVariableV2(variableSubj.variableName)

                    if (typeInspectionResultWhere.typedEntities.contains(key)) {
                        //println(key, typeInspectionResult.typedEntities(key))

                        val additionalStatements = typeInspectionResultWhere.typedEntities(key) match {
                            case nonPropTypeInfo: NonPropertyTypeInfoV2 =>
                                createAdditionalStatementsForNonPropertyType(nonPropTypeInfo.typeIri, statementP.subj, index)
                            case propTypeInfo: PropertyTypeInfoV2 =>
                                Vector.empty[ExtendedSearchStatementPattern]
                        }

                        // prevent that the same type info is processed more than once
                        typeInspectionResultWhere = TypeInspectionResultV2(
                            typedEntities = typeInspectionResultWhere.typedEntities - key
                        )

                        additionalStatements

                    } else {
                        Vector.empty[ExtendedSearchStatementPattern]
                    }


                case iriSubj: ExtendedSearchInternalEntityIri =>
                    val key = TypeableIriV2(iriSubj.iri)

                    Vector.empty[ExtendedSearchStatementPattern]

                case other => Vector.empty[ExtendedSearchStatementPattern]
            }


            val additionalStatementsForPred: Vector[ExtendedSearchStatementPattern] = statementP.pred match {
                case variablePred: ExtendedSearchVar =>
                    val key = TypeableVariableV2(variablePred.variableName)

                    if (typeInspectionResultWhere.typedEntities.contains(key)) {

                        val additionalStatements = typeInspectionResultWhere.typedEntities(key) match {
                            case nonPropTypeInfo: NonPropertyTypeInfoV2 =>
                                Vector.empty[ExtendedSearchStatementPattern]

                            case propTypeInfo: PropertyTypeInfoV2 =>
                                createAdditionalStatementsForPropertyType(propTypeInfo.objectTypeIri, statementP.subj, statementP.obj, index)
                        }

                        // prevent that the same type info is processed more than once
                        typeInspectionResultWhere = TypeInspectionResultV2(
                            typedEntities = typeInspectionResultWhere.typedEntities - key
                        )

                        additionalStatements

                    } else {
                        Vector.empty[ExtendedSearchStatementPattern]
                    }

                case iriPred: ExtendedSearchInternalEntityIri =>

                    Vector.empty[ExtendedSearchStatementPattern]

                case other => Vector.empty[ExtendedSearchStatementPattern]
            }

            val additionalStatementsForObj: Vector[ExtendedSearchStatementPattern] = statementP.obj match {
                case variableObj: ExtendedSearchVar => Vector.empty[ExtendedSearchStatementPattern]

                case internalEntityIriObj: ExtendedSearchInternalEntityIri => Vector.empty[ExtendedSearchStatementPattern]

                case iriObj: ExtendedSearchIri =>
                    val key = TypeableIriV2(iriObj.iri)

                    if (typeInspectionResultWhere.typedEntities.contains(key)) {
                        //println(key, typeInspectionResult.typedEntities(key))

                        val additionalStatements = typeInspectionResultWhere.typedEntities(key) match {
                            case nonPropTypeInfo: NonPropertyTypeInfoV2 =>
                                createAdditionalStatementsForNonPropertyType(nonPropTypeInfo.typeIri, statementP.obj, index)
                            case propTypeInfo: PropertyTypeInfoV2 =>
                                Vector.empty[ExtendedSearchStatementPattern]

                        }

                        // prevent that the same type info is processed more than once
                        typeInspectionResultWhere = TypeInspectionResultV2(
                            typedEntities = typeInspectionResultWhere.typedEntities - key
                        )

                        additionalStatements

                    } else {
                        Vector.empty[ExtendedSearchStatementPattern]
                    }

                case other => Vector.empty[ExtendedSearchStatementPattern]
            }

            additionalStatementsForSubj ++ additionalStatementsForPred ++ additionalStatementsForObj

        }

        def convertQueryPatterns(patterns: Seq[ExtendedSearchQueryPattern]): Seq[ExtendedSearchQueryPattern] = {
            patterns.flatMap {
                case (whereP: ExtendedSearchQueryPattern) =>
                    whereP match {
                        case statementP: ExtendedSearchStatementPattern =>

                            Vector(statementP)

                        case optionalP: ExtendedSearchOptionalPattern =>
                            Seq(ExtendedSearchOptionalPattern(convertQueryPatterns(optionalP.patterns)))

                        case unionP: ExtendedSearchUnionPattern =>
                            val blocks = unionP.blocks.map {
                                blockPatterns: Seq[ExtendedSearchQueryPattern] => convertQueryPatterns(blockPatterns)
                            }

                            Seq(ExtendedSearchUnionPattern(blocks))

                        case filterP: ExtendedSearchFilterPattern => Seq(filterP) // TODO: transform filter, possibly adding more statements (text, date comparison etc.)
                    }
            }
        }

        val additionalStatements: Seq[ExtendedSearchStatementPattern] = extendedSearchWhereClausePatternsWithOriginalFilters.collect {
            case statementP: ExtendedSearchStatementPattern => statementP
        }.zipWithIndex.flatMap {
            case (statementP, index) =>
                addAdditionalStatementsForStatement(statementP, index)
        }

        // convert where statements (handle FILTER expressions)
        val extendedSearchWhereClausePatternsConverted: Seq[ExtendedSearchQueryPattern] = convertQueryPatterns(extendedSearchWhereClausePatternsWithOriginalFilters)

        val constructQuery = ExtendedSearchQuery(
            constructClause = extendedSearchConstructClauseStatementPatterns.toVector ++ additionalStatements,
            whereClause = extendedSearchWhereClausePatternsConverted.toVector ++ additionalStatements
        )

        for {

        searchSparql <- Future(queries.sparql.v2.txt.searchExtended(
            triplestore = settings.triplestoreType,
            query = constructQuery
        ).toString())

        //_ = println(searchSparql)



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