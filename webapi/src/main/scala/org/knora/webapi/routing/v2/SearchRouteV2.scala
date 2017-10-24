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
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.search.v2.SearchParserV2
import org.knora.webapi.{BadRequestException, IRI, SettingsImpl}

import scala.language.postfixOps

/**
  * Provides a spray-routing function for API routes that deal with search.
  */
object SearchRouteV2 extends Authenticator {
    val LIMIT_TO_PROJECT = "limitToProject"
    val LIMIT_TO_RESOURCE_CLASS = "limitToResourceClass"

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")
        val stringFormatter = StringFormatter.getInstance

        path("v2" / "search" / Segment) { searchval => // TODO: if a space is encoded as a "+", this is not converted back to a space
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val searchString = stringFormatter.toSparqlEncodedString(searchval, () => throw BadRequestException(s"Invalid search string: '$searchval'"))

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
        } ~ path("v2" / "searchextended" / Segment) { sparql => // Segment is a URL encoded string representing a Sparql query
            get {

                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val constructQuery = SearchParserV2.parseSearchQuery(sparql)

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

                    val searchString = stringFormatter.toSparqlEncodedString(searchval, () => throw BadRequestException(s"Invalid search string: '$searchval'"))

                    val params: Map[String, String] = requestContext.request.uri.query().toMap

                    val limitToProjectIriStr = params.get(LIMIT_TO_PROJECT)

                    val limitToProject: Option[IRI] = limitToProjectIriStr match {

                        case Some(projectIriStr: String) =>
                            val projectIri = stringFormatter.toIri(projectIriStr, () => throw BadRequestException(s"$projectIriStr is not a valid Iri"))

                            Some(projectIri)

                        case None => None

                    }

                    val limitToResourceClassIriStr = params.get(LIMIT_TO_RESOURCE_CLASS)

                    val limitToResourceClass: Option[IRI] = limitToResourceClassIriStr match {

                        case Some(resourceClassIriStr: String) =>

                            val externalResourceClassIri = stringFormatter.toIri(resourceClassIriStr, () => throw BadRequestException(s"$resourceClassIriStr is not a valid Iri"))

                            Some(stringFormatter.externalApiV2WithValueObjectEntityIriToInternalEntityIri(externalResourceClassIri, () => throw BadRequestException(s"$externalResourceClassIri is not a valid knora-api with value object resource class Iri")))

                        case None => None

                    }

                    val requestMessage = SearchResourceByLabelRequestV2(searchValue = searchString, limitToProject = limitToProject, limitToResourceClass = limitToResourceClass,userProfile = userProfile)

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