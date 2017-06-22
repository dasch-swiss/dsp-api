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
      * @param constructQuery Sparql construct query provided by the client.
      * @param userProfile the profile of the client making the request.
      * @return a [[ReadResourcesSequenceV2]] representing the resources that have been found.
      */
    private def extendedSearchV2(constructQuery: SimpleConstructQuery, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {

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

        def getPossiblePropIriForPropertyVariable(filter: ExtendedSearchFilterExpression, propertyVar: ExtendedSearchVar): Vector[(String, IRI)] = {
            filter match {
                case compFilter: ExtendedSearchCompareExpression =>
                    // check if propertyVar is the left arg, the comparison operator is a "=" and the right arg is an internal entity Iri

                    compFilter.leftArg match {
                        case propVar: ExtendedSearchVar if propVar.variableName == propertyVar.variableName =>

                            if (compFilter.operator == "=") {
                                compFilter.rightArg match {
                                    case objectClassConstr: ExtendedSearchInternalEntityIri =>
                                        Vector((propVar.variableName, objectClassConstr.iri))
                                    case _ => Vector.empty[(String,String)]
                                }
                            } else {
                                Vector.empty[(String,String)]
                            }
                        case _ => Vector.empty[(String,String)]

                    }

                case andFilter: ExtendedSearchAndExpression =>
                    getPossiblePropIriForPropertyVariable(andFilter.leftArg, propertyVar) ++
                    getPossiblePropIriForPropertyVariable(andFilter.rightArg, propertyVar)

                case orFilter: ExtendedSearchOrExpression =>
                    getPossiblePropIriForPropertyVariable(orFilter.leftArg, propertyVar) ++
                    getPossiblePropIriForPropertyVariable(orFilter.rightArg, propertyVar)


                case _ => Vector.empty[(String,String)]

            }
        }

        // get all the statement patterns from the where clause
        val statementPatterns: Seq[StatementPattern] = constructQuery.whereClause.statements.collect {
            case statementP: StatementPattern => statementP
        }

        // convert the statement patterns to a sequence of `ExtendedSearchStatementPattern`.
        val extendedSearchStatementPatterns: Seq[ExtendedSearchStatementPattern] = statementPatterns.map {
            (statementP: StatementPattern) =>

                val subj = convertSearchParserEntityToExtendedSearchEntity(statementP.subj)

                val pred = convertSearchParserEntityToExtendedSearchEntity(statementP.pred)

                val obj = convertSearchParserEntityToExtendedSearchEntity(statementP.obj)

                ExtendedSearchStatementPattern(
                    subj = subj,
                    pred = pred,
                    obj = obj
                )

        }
        

        // collect all the property Iris used in the predicates of statement patterns
        val propertyIrisFromPredicates: Set[IRI] = extendedSearchStatementPatterns.map(_.pred).collect {
            case propIri: ExtendedSearchInternalEntityIri => propIri.iri
        }.toSet

        // collect all the variables representing properties used in the predicates of statement patterns
        val propertyVariablesFromPredicates: Set[ExtendedSearchVar] = extendedSearchStatementPatterns.map(_.pred).collect {
            case propVar: ExtendedSearchVar => propVar
        }.toSet

        // collect all the internal entity Iris used in the objects of statement patterns
        val internalEntityIrisFromObjects: Set[IRI] = extendedSearchStatementPatterns.map(_.obj).collect {
            case resClassIri: ExtendedSearchInternalEntityIri => resClassIri.iri
        }.toSet

        // collect all the variables used in the objects of statement patterns
        val variablesFromObjects: Set[ExtendedSearchVar] = extendedSearchStatementPatterns.map(_.obj).collect {
            case resClassVar: ExtendedSearchVar => resClassVar
        }.toSet

        // collect ll the filter patterns
        val filterPatterns: Seq[FilterExpression] = constructQuery.whereClause.statements.collect {
            case filterP: FilterPattern =>
                filterP.expression match {
                    case filterExpr: FilterExpression => filterExpr
                }
        }

        // convert the filter patterns to a sequence of `ExtendedSearchFilterExpression`
        val extendedSearchFilterPatterns: Seq[ExtendedSearchFilterExpression] = filterPatterns.map {
            (filterP: FilterExpression) =>
                convertSearchParserEntityToExtendedSearchEntity(filterP)
        }

        // search for propertyVariablesFromPredicates in filter patterns in order to get the possible property Iris
        val propertyVarPossiblePropIris: Map[String, Vector[(String, IRI)]] = propertyVariablesFromPredicates.foldLeft(Vector.empty[(String, IRI)]) {
            case (acc, propVar) =>
                acc ++ extendedSearchFilterPatterns.foldLeft(Vector.empty[(String, IRI)]) {
                    case (acc, filter) =>
                        acc ++ getPossiblePropIriForPropertyVariable(filter, propVar)
                }
        }.groupBy{
            case (propVar, propIri) => propVar
        }

        val propertyIris: Set[IRI] = propertyVarPossiblePropIris.flatMap {
            case (propVar: String, propIris: Vector[(String, IRI)]) =>
                propIris.map {
                    case (propVar, propIri) => propIri
                }
        }.toSet



        for {

            entityInfo: EntityInfoGetResponseV2 <- (responderManager ? EntityInfoGetRequestV2(propertyIris = propertyIrisFromPredicates ++ propertyIris, userProfile = userProfile)).mapTo[EntityInfoGetResponseV2]

            propVarsWithTypes: Map[String, Vector[PropertyVarType]] = propertyVarPossiblePropIris.map {
                case (propVar, propIris) =>

                    val propTypes = propIris.map {
                        case (propVar: String, propIri: IRI) =>

                            val propInfo: PropertyEntityInfoV1 = entityInfo.propertyEntityInfoMap.getOrElse(propIri, throw BadRequestException(s"$propIri is not a known property"))

                            PropertyVarType(propVar = ExtendedSearchVar(propVar), isLinkingProp = propInfo.isLinkProp, objectConstraint = propInfo.getPredicateObjects(OntologyConstants.KnoraBase.ObjectClassConstraint).head, propertyIri = propIri)
                    }

                propVar -> propTypes
            }

            _ = println(propVarsWithTypes)





            searchSparql <- Future(queries.sparql.v2.txt.searchExtended(
                triplestore = settings.triplestoreType,
                query = ExtendedSearchQuery(constructClause = Vector.empty[ExtendedSearchStatementPattern], whereClause = Vector.empty[ExtendedSearchStatementsAndFilterPatterns])
            ).toString())

            searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(searchSparql)).mapTo[SparqlConstructResponse]


            // separate resources and value objects
            queryResultsSeparated = ConstructResponseUtilV2.splitResourcesAndValueRdfData(constructQueryResults = searchResponse, userProfile = userProfile)

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