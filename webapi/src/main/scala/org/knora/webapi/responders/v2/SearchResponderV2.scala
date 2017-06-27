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
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.store.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse}
import org.knora.webapi.messages.v1.responder.ontologymessages.PropertyEntityInfoV1
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.ontologymessages.{EntityInfoGetRequestV2, EntityInfoGetResponseV2}
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


    case class PropertyVarType(propVar: ExtendedSearchVar, isLinkingProp: Boolean, objectConstraint: IRI, propertyIri: IRI)

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
          * Converts a [[FilterExpression]] to an [[ExtendedSearchFilterExpression]].
          *
          * @param entity the entity provided by [[SearchParserV2]].
          * @return a [[ExtendedSearchFilterExpression]].
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

        def convertSearchParserStatementPatternToExtendedSearchStatement(statementPattern: StatementPattern, disableInference: Boolean): ExtendedSearchStatementPattern = {
            val subj = convertSearchParserEntityToExtendedSearchEntity(statementPattern.subj)
            val pred = convertSearchParserEntityToExtendedSearchEntity(statementPattern.pred)
            val obj = convertSearchParserEntityToExtendedSearchEntity(statementPattern.obj)

            val reallyDisableInference = disableInference || (pred match {
                case variable: ExtendedSearchVar => true
                case _ => false
            })

            ExtendedSearchStatementPattern(
                subj = subj,
                pred = pred,
                obj = obj,
                disableInference = reallyDisableInference
            )
        }


        def convertSearchParserQueryPatternsToExtendedSearchPatterns(patterns: Seq[QueryPattern]): Seq[ExtendedSearchQueryPattern] = {
            // convert the statement patterns to a sequence of `ExtendedSearchStatementPattern`.
            patterns.map {
                (queryP: QueryPattern) =>
                    queryP match {
                        case statementPattern: StatementPattern =>
                            convertSearchParserStatementPatternToExtendedSearchStatement(statementPattern = statementPattern, disableInference = false)

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
        val typeInspectionResult: TypeInspectionResultV2 = typeInspector.inspectTypes(simpleConstructQuery.whereClause)
        val extendedSearchWhereClausePatterns: Seq[ExtendedSearchQueryPattern] = convertSearchParserQueryPatternsToExtendedSearchPatterns(whereClauseWithoutAnnotations.patterns)
        val constructStatementPatterns: Seq[ExtendedSearchStatementPattern] = convertSearchParserConstructPatternsToExtendedSearchPatterns(simpleConstructQuery.constructClause.statements)

        def createAdditionalStatementsBasedOnTypeInfo(patterns: Seq[ExtendedSearchQueryPattern]): Seq[ExtendedSearchQueryPattern] = {
            patterns.flatMap {
                (whereP: ExtendedSearchQueryPattern) =>
                    whereP match {
                        case statementP: ExtendedSearchStatementPattern => createAdditionalStatementsForSingleStatement(statementP)

                        case optionalP: ExtendedSearchOptionalPattern =>
                            Seq(ExtendedSearchOptionalPattern(createAdditionalStatementsBasedOnTypeInfo(optionalP.patterns)))

                        case unionP: ExtendedSearchUnionPattern =>
                            val blocks = unionP.blocks.map {
                                blockPatterns: Seq[ExtendedSearchQueryPattern] => createAdditionalStatementsBasedOnTypeInfo(blockPatterns)
                            }

                            Seq(ExtendedSearchUnionPattern(blocks))

                        case filterP: ExtendedSearchFilterPattern => Seq(filterP) // TODO: transform filter, possibly adding more statements
                    }
            }
        }

        def createAdditionalStatementsForSingleStatement(statementPattern: ExtendedSearchStatementPattern): Seq[ExtendedSearchStatementPattern] = {
            // TODO: look at typeInspectionResult to see what additional statements need to be created



            Vector.empty[ExtendedSearchStatementPattern]
        }

        val t = 1



/*
        // create additional statements for resources
        val additionalResourceStatements: Vector[ExtendedSearchStatementPattern] = extendedSearchWhereClausePatterns.filter {
            (statementP: ExtendedSearchStatementPattern) =>

                statementP.pred == ExtendedSearchIri(OntologyConstants.Rdf.Type) && statementP.obj == ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.Resource)
        }.zipWithIndex.flatMap {
            case (resStatementP: ExtendedSearchStatementPattern, index: Int) =>

                Vector(
                    ExtendedSearchStatementPattern(subj = resStatementP.subj, pred = ExtendedSearchIri(OntologyConstants.Rdfs.Label), obj = ExtendedSearchVar("resourceLabel" + index), false),
                    ExtendedSearchStatementPattern(subj = resStatementP.subj, pred = ExtendedSearchIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchVar("resourceType" + index), false),
                    ExtendedSearchStatementPattern(subj = resStatementP.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.AttachedToUser), obj = ExtendedSearchVar("resourceCreator" + index), false),
                    ExtendedSearchStatementPattern(subj = resStatementP.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasPermissions), obj = ExtendedSearchVar("resourcePermissions" + index), false),
                    ExtendedSearchStatementPattern(subj = resStatementP.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.AttachedToProject), obj = ExtendedSearchVar("resourceProject" + index), false)
                )
        }

        // create additional statements for linking properties (get the value object, reification)
        val additionalLinkPropStatements: Vector[ExtendedSearchStatementPattern] = whereExtendedSearchStatementPatterns.filter {
            (statementP: ExtendedSearchStatementPattern) =>

                statementP.pred == ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasLinkTo)
        }.zipWithIndex.flatMap {
            case (statementP: ExtendedSearchStatementPattern, index: Int) =>

                Vector(
                    //ExtendedSearchStatementPattern(subj = statementP.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasLinkToValue), obj = ExtendedSearchVar("linkValueObj" + index), true),
                    ExtendedSearchStatementPattern(subj = statementP.subj, pred = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.HasValue), obj = ExtendedSearchVar("linkValueObj" + index), true),
                    ExtendedSearchStatementPattern(subj = statementP.subj, pred = ExtendedSearchVar("linkValueProp" + index), obj = ExtendedSearchVar("linkValueObj" + index), false),
                    ExtendedSearchStatementPattern(subj = ExtendedSearchVar("linkValueObj" + index), pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Type), obj = ExtendedSearchInternalEntityIri(OntologyConstants.KnoraBase.LinkValue), false),
                    ExtendedSearchStatementPattern(subj = ExtendedSearchVar("linkValueObj" + index), pred = ExtendedSearchIri(OntologyConstants.Rdf.Subject), obj = statementP.subj, false),
                    ExtendedSearchStatementPattern(subj = ExtendedSearchVar("linkValueObj" + index), pred = ExtendedSearchInternalEntityIri(OntologyConstants.Rdf.Object), obj = statementP.obj, false),
                    ExtendedSearchStatementPattern(subj = ExtendedSearchVar("linkValueObj" + index), pred = ExtendedSearchVar("linkValueObjProp" + index), obj = ExtendedSearchVar("linkValueObjVal" + index), false)

                )

        }

        val whereClause = ExtendedSearchStatementsAndFilterPatterns(statements = whereExtendedSearchStatementPatterns ++ additionalResourceStatements ++ additionalLinkPropStatements, filters = extendedSearchFilterPatterns)

        val constructQuery: ExtendedSearchQuery = ExtendedSearchQuery(constructClause = constructExtendedSearchStatementPatterns ++ additionalResourceStatements ++ additionalLinkPropStatements, whereClause = whereClause)
*/

        for {

            test <- Future(1)

        /*searchSparql <- Future(queries.sparql.v2.txt.searchExtended(
            triplestore = settings.triplestoreType,
            query = constructQuery
        ).toString())

        //_ = println(searchSparql)

        searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(searchSparql)).mapTo[SparqlConstructResponse]*/


        // separate resources and value objects
        //queryResultsSeparated: Map[IRI, ConstructResponseUtilV2.ResourceWithValueRdfData] = ConstructResponseUtilV2.splitResourcesAndValueRdfData(constructQueryResults = searchResponse, userProfile = userProfile)

        } yield ReadResourcesSequenceV2(numberOfResources = 0, resources = Vector.empty[ReadResourceV2])

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