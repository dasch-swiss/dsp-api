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

package org.knora.webapi.routing.v2

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.messages.v2.responder.searchmessages.{ExtendedSearchGetRequestV2, FulltextSearchGetRequestV2, SearchResourceByLabelRequestV2}
import org.knora.webapi.routing.{Authenticator, RouteUtilV2}
import org.knora.webapi.util.InputValidation
import org.knora.webapi.util.search.v2.SearchParserV2
import org.knora.webapi.{BadRequestException, SettingsImpl}

import scala.language.postfixOps

/**
  * Provides a spray-routing function for API routes that deal with search.
  */
object SearchRouteV2 extends Authenticator {


    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v2" / "search" / Segment) { searchval => // TODO: if a space is encoded as a "+", this is not converted back to a space
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val searchString = InputValidation.toSparqlEncodedString(searchval, () => throw BadRequestException(s"Invalid search string: '$searchval'"))

                    val requestMessage = FulltextSearchGetRequestV2(searchValue = searchString, userProfile = userProfile)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            }
        } ~ path("v2" / "searchextended" / Segment) { sparql =>
            get {

                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    // TODO: process Sparql provided by the user

                    val sparql_tmp =
                        """
                          |PREFIX beol: <http://api.knora.org/ontology/beol/simple/v2#>
                          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                          |
                          |CONSTRUCT {
                          |    ?letter knora-api:isMainResource true .
                          |
                          |    ?letter ?linkingProp1  <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
                          |    ?letter ?linkingProp2  <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
                          |} WHERE {
                          |    ?letter a beol:letter .
                          |
                          |    # Scheuchzer, Johann Jacob 1672-1733
                          |    ?letter ?linkingProp1  <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
                          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient )
                          |
                          |    # Hermann, Jacob 1678-1733
                          |    ?letter ?linkingProp2 <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
                          |    FILTER(?linkingProp2 = beol:hasAuthor || ?linkingProp2 = beol:hasRecipient )
                          |}
                        """.stripMargin

                    val constructQuery = SearchParserV2.parseSearchQuery(sparql_tmp)

                    val requestMessage = ExtendedSearchGetRequestV2(constructQuery = constructQuery, userProfile = userProfile)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }


            }


        } ~ path("v2" / "searchbylabel" / Segment) { searchval => // TODO: if a space is encoded as a "+", this is not converted back to a space
            get {
                requestContext => {

                    val userProfile = getUserProfileV1(requestContext)

                    val searchString = InputValidation.toSparqlEncodedString(searchval, () => throw BadRequestException(s"Invalid search string: '$searchval'"))

                    val requestMessage = SearchResourceByLabelRequestV2(searchValue = searchString, userProfile = userProfile)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            }

        }
    }


}