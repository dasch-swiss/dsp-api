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
      * @param userProfile the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def extendedSearchV2(simpleConstructQuery: SimpleConstructQuery, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

        /**
          * Converts a [[FilterExpression]] to an [[ExtendedSearchFilterExpression]].
          *
          * @param entity the entity provided by [[SearchParserV2]].
          * @return a [[ExtendedSearchFilterExpression]].
          */
        def convertSearchParserEntityToExtendedSearchEntity(entity: FilterExpression): ExtendedSearchFilterExpression = {
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
                case compareExpr: CompareExpression => ExtendedSearchCompareExpression(leftArg = convertSearchParserEntityToExtendedSearchEntity(compareExpr.leftArg), operator = compareExpr.operator, rightArg = convertSearchParserEntityToExtendedSearchEntity(compareExpr.rightArg))
                case andExpr: AndExpression => ExtendedSearchAndExpression(leftArg = convertSearchParserEntityToExtendedSearchEntity(andExpr.leftArg), rightArg = convertSearchParserEntityToExtendedSearchEntity(andExpr.rightArg))
                case orExpr: OrExpression => ExtendedSearchOrExpression(leftArg = convertSearchParserEntityToExtendedSearchEntity(orExpr.leftArg), rightArg = convertSearchParserEntityToExtendedSearchEntity(orExpr.rightArg))
            }
        }

        // get all the statement patterns from the construct clause
        val constructStatementPatterns: Seq[StatementPattern] = simpleConstructQuery.constructClause.statements.collect {
            case statementP: StatementPattern => statementP
        }

        // convert the statement patterns to a sequence of `ExtendedSearchStatementPattern`.
        val constructExtendedSearchStatementPatterns: Vector[ExtendedSearchStatementPattern] = constructStatementPatterns.map {
            (statementP: StatementPattern) =>

                val subj = convertSearchParserEntityToExtendedSearchEntity(statementP.subj)

                val pred = convertSearchParserEntityToExtendedSearchEntity(statementP.pred)

                val obj = convertSearchParserEntityToExtendedSearchEntity(statementP.obj)

                ExtendedSearchStatementPattern(
                    subj = subj,
                    pred = pred,
                    obj = obj
                )

        }.toVector

        // get all the statement patterns from the where clause
        val whereStatementPatterns: Seq[StatementPattern] = simpleConstructQuery.whereClause.statements.collect {
            case statementP: StatementPattern => statementP
        }

        // convert the statement patterns to a sequence of `ExtendedSearchStatementPattern`.
        val whereExtendedSearchStatementPatterns: Vector[ExtendedSearchStatementPattern] = whereStatementPatterns.map {
            (statementP: StatementPattern) =>

                val subj = convertSearchParserEntityToExtendedSearchEntity(statementP.subj)

                val pred = convertSearchParserEntityToExtendedSearchEntity(statementP.pred)

                val obj = convertSearchParserEntityToExtendedSearchEntity(statementP.obj)

                ExtendedSearchStatementPattern(
                    subj = subj,
                    pred = pred,
                    obj = obj
                )

        }.toVector

        // collect all the filter patterns
        val filterPatterns: Vector[FilterExpression] = simpleConstructQuery.whereClause.statements.collect {
            case filterP: FilterPattern =>
                filterP.expression match {
                    case filterExpr: FilterExpression => filterExpr
                }
        }.toVector

        val extendedSearchFilterPatterns: Vector[ExtendedSearchFilterExpression] = filterPatterns.map {
            (filterP: FilterExpression) =>
                convertSearchParserEntityToExtendedSearchEntity(filterP)
        }

        // create additional statements for resources
        val additionalResourceStatements: Vector[ExtendedSearchStatementPattern] = whereExtendedSearchStatementPatterns.filter {
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