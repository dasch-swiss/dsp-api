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
import org.knora.webapi.{BadRequestException, IRI, NotImplementedException}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse}
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.searchmessages.{ExtendedSearchGetRequestV2, FulltextSearchGetRequestV2, SearchResourceByLabelRequestV2}
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{ConstructResponseUtilV2, InputValidation}
import org.knora.webapi.util.search.v2._

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

        } yield ReadResourcesSequenceV2(numberOfResources = queryResultsSeparated.size, resources = ConstructResponseUtilV2.createSearchResponse(queryResultsSeparated, settings), settings = settings)

    }

    sealed trait ExtendedSearchEntity

    case class ExtendedSearchVar(variableName: String) extends ExtendedSearchEntity

    case class ExtendedSearchIri(iri: IRI) extends ExtendedSearchEntity

    case class ExtendedSearchXsdLiteral(value: String, datatype: IRI) extends ExtendedSearchEntity

    private def extendedSearchV2(constructQuery: SimpleConstructQuery, userProfile: UserProfileV1): Future[ReadResourcesSequenceV2] = {



        def convertExternalEntityToInternalEntity(entity: Entity): ExtendedSearchEntity = {
            // convert external Iris to internal Iris if needed

            entity match {
                case iriRef: IriRef => ExtendedSearchIri(InputValidation.externalIriToInternalIri(iriRef.iri))
                case queryVar: QueryVariable => ExtendedSearchVar(queryVar.variableName)
                case literal: XsdLiteral => ExtendedSearchXsdLiteral(value = literal.value, datatype = literal.datatype)
            }
        }

        val statementPatterns: Seq[StatementPattern] = constructQuery.whereClause.statements.collect {
            case statementP: StatementPattern => statementP
        }

        // check if the statements patterns contain external v2 Iris and convert them to internal Iris if necessary
        val statementPatternsConverted = statementPatterns.map {
            (statementP: StatementPattern) =>

                (
                    convertExternalEntityToInternalEntity(statementP.subj),
                    convertExternalEntityToInternalEntity(statementP.pred),
                    convertExternalEntityToInternalEntity(statementP.obj)
                )

        }

        /*val statementPatternsGroupedBySubj: Map[Entity, Seq[StatementPattern]] = statementPatternsConverted.groupBy {
            (statementP: StatementPattern) =>
                statementP.subj
        }

        val filterPatterns: Seq[FilterPattern] = constructQuery.whereClause.statements.collect {
            case filterP: FilterPattern => filterP
        }*/



        for {


            searchSparql <- Future(queries.sparql.v2.txt.searchExtended(
                triplestore = settings.triplestoreType,
                query = constructQuery
            ).toString())

            searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(searchSparql)).mapTo[SparqlConstructResponse]

            // separate resources and value objects
            queryResultsSeparated = ConstructResponseUtilV2.splitResourcesAndValueRdfData(constructQueryResults = searchResponse, userProfile = userProfile)

        } yield ReadResourcesSequenceV2(numberOfResources = queryResultsSeparated.size, resources = ConstructResponseUtilV2.createSearchResponse(queryResultsSeparated, settings), settings = settings)

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

        } yield ReadResourcesSequenceV2(numberOfResources = queryResultsSeparated.size, resources = ConstructResponseUtilV2.createSearchResponse(queryResultsSeparated, settings), settings = settings)


    }
}