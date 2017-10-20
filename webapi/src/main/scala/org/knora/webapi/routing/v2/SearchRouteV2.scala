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
import org.knora.webapi.messages.v2.responder.searchmessages._
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
    val OFFSET = "offset"
    val stringFormatter = StringFormatter.getInstance

    /**
      * Gets the requested offset. Returns zero if no offset is indicated.
      *
      * @param params the GET parameters.
      * @return the offset to be used for paging.
      */
    private def getOffsetFromParams(params: Map[String, String]): Int = {
        val offsetStr = params.get(OFFSET)

        offsetStr match {
            case Some(offset: String) =>

                val offsetInt: Int = stringFormatter.toInt(offset, () => throw BadRequestException(s"offset is expected to be an Integer, but $offset given"))

                if (offsetInt < 0) throw BadRequestException(s"offset must be an Integer >= 0, but $offsetInt given.")

                offsetInt


            case None => 0
        }
    }

    /**
      * Gets the the project the search should be restricted to, if any.
      *
      * @param params the GET parameters.
      * @return the project Iri, if any.
      */
    private def getProjectFromParams(params: Map[String, String]): Option[IRI] = {
        val limitToProjectIriStr = params.get(LIMIT_TO_PROJECT)

        val limitToProjectIri: Option[IRI] = limitToProjectIriStr match {

            case Some(projectIriStr: String) =>
                val projectIri = stringFormatter.toIri(projectIriStr, () => throw BadRequestException(s"$projectIriStr is not a valid Iri"))

                Some(projectIri)

            case None => None

        }

        limitToProjectIri

    }

    /**
      * Gets the resource class the search should be restricted to, if any.
      *
      * @param params the GET parameters.
      * @return the internal resource class, if any.
      */
    private def getResourceClassFromParams(params: Map[String, String]): Option[IRI] = {

        val limitToResourceClassIriStr = params.get(LIMIT_TO_RESOURCE_CLASS)

        val limitToResourceClassIri: Option[IRI] = limitToResourceClassIriStr match {

            case Some(resourceClassIriStr: String) =>

                val externalResourceClassIri = stringFormatter.toIri(resourceClassIriStr, () => throw BadRequestException(s"$resourceClassIriStr is not a valid Iri"))

                Some(stringFormatter.externalToInternalEntityIri(externalResourceClassIri, () => throw BadRequestException(s"$externalResourceClassIri is not a valid knora-api resource class Iri")))

            case None => None

        }

        limitToResourceClassIri
    }

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v2" / "search" / "count" / Segment) { searchval => // TODO: if a space is encoded as a "+", this is not converted back to a space
            get {
                requestContext =>

                    val userProfile = getUserProfileV1(requestContext)

                    val searchString = stringFormatter.toSparqlEncodedString(searchval, () => throw BadRequestException(s"Invalid search string: '$searchval'"))

                    val params: Map[String, String] = requestContext.request.uri.query().toMap

                    val limitToProject: Option[IRI] = getProjectFromParams(params)

                    val limitToResourceClass: Option[IRI] = getResourceClassFromParams(params)

                    val requestMessage = FullTextSearchCountGetRequestV2(searchValue = searchString, limitToProject = limitToProject, limitToResourceClass = limitToResourceClass, userProfile = userProfile)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~ path("v2" / "search" / Segment) { searchval => // TODO: if a space is encoded as a "+", this is not converted back to a space
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val searchString = stringFormatter.toSparqlEncodedString(searchval, () => throw BadRequestException(s"Invalid search string: '$searchval'"))

                    val params: Map[String, String] = requestContext.request.uri.query().toMap

                    val offset = getOffsetFromParams(params)

                    val limitToProject: Option[IRI] = getProjectFromParams(params)

                    val limitToResourceClass: Option[IRI] = getResourceClassFromParams(params)

                    val requestMessage = FulltextSearchGetRequestV2(searchValue = searchString, offset = offset, limitToProject = limitToProject, limitToResourceClass = limitToResourceClass, userProfile = userProfile)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            }
        } ~ path("v2" / "searchextended" / "count" / Segment) { sparql => // Segment is a URL encoded string representing a Sparql query
            get {

                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val constructQuery = SearchParserV2.parseSearchQuery(sparql)

                    val requestMessage = ExtendedSearchCountGetRequestV2(constructQuery = constructQuery, userProfile = userProfile)

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


        } ~ path("v2" / "searchbylabel" / "count" / Segment) { searchval => // TODO: if a space is encoded as a "+", this is not converted back to a space
            get {
                requestContext => {
                    val userProfile = getUserProfileV1(requestContext)

                    val searchString = stringFormatter.toSparqlEncodedString(searchval, () => throw BadRequestException(s"Invalid search string: '$searchval'"))

                    val params: Map[String, String] = requestContext.request.uri.query().toMap

                    val limitToProject: Option[IRI] = getProjectFromParams(params)

                    val limitToResourceClass: Option[IRI] = getResourceClassFromParams(params)

                    val requestMessage = SearchResourceByLabelCountGetRequestV2(
                        searchValue = searchString,
                        limitToProject = limitToProject,
                        limitToResourceClass = limitToResourceClass,
                        userProfile = userProfile
                    )

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

                    val offset = getOffsetFromParams(params)

                    val limitToProject: Option[IRI] = getProjectFromParams(params)

                    val limitToResourceClass: Option[IRI] = getResourceClassFromParams(params)

                    val requestMessage = SearchResourceByLabelGetRequestV2(
                        searchValue = searchString,
                        offset = offset,
                        limitToProject = limitToProject,
                        limitToResourceClass = limitToResourceClass,
                        userProfile = userProfile
                    )

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