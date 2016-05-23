/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.routing.v1

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import org.knora.webapi.messages.v1.responder.searchmessages.{ExtendedSearchGetRequestV1, FulltextSearchGetRequestV1, SearchComparisonOperatorV1}
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.InputValidation
import org.knora.webapi.{BadRequestException, IRI, SettingsImpl}
import spray.routing.Directives._
import spray.routing._

import scala.language.postfixOps
import scala.util.Try

// slash after path without following segment

/**
  * Provides a spray-routing function for API routes that deal with search.
  */
object SearchRouteV1 extends Authenticator {

    /**
      * The default number of rows to show in search results.
      */
    private val defaultShowNRows = 25

    def makeExtendedSearchRequestMessage(userProfile: UserProfileV1, reverseParams: Map[String, Seq[String]]): ExtendedSearchGetRequestV1 = {
        // Spray returns the parameters in reverse order, so reverse them before processing, because the JavaScript GUI expects the order to be preserved.
        val params = reverseParams.map {
            case (key, value) => key -> value.reverse
        }

        //println(params)

        params.get("searchtype") match {
            case Some(List("extended")) => ()
            case other => throw BadRequestException(s"Unexpected searchtype param for extended search")
        }

        // only one value is expected
        val restypeIri: Option[IRI] = params.get("filter_by_restype") match {
            case Some(List(restype: IRI)) => Some(InputValidation.toIri(restype, () => throw BadRequestException(s"Unexpected param 'filter_by_restype' for extended search: $restype")))
            case other => None
        }

        // only one value is expected
        val projectIri: Option[IRI] = params.get("filter_by_project") match {
            case Some(List(project: IRI)) => Some(InputValidation.toIri(project, () => throw BadRequestException(s"Unexpected param 'filter_by_project' for extended search: $project")))
            case other => None
        }

        // only one value is expected
        val ownerIri: Option[IRI] = params.get("filter_by_owner") match {
            case Some(List(owner: IRI)) => Some(InputValidation.toIri(owner, () => throw BadRequestException(s"Unexpected param 'filter_by_owner' for extended search: $owner")))
            case other => None
        }

        // here, also multiple values can be given
        val propertyIri: Seq[IRI] = params.get("property_id") match {
            case Some(propertyList: Seq[IRI]) => propertyList.map(
                prop => InputValidation.toIri(prop, () => throw BadRequestException(s"Unexpected param 'property_id' for extended search: $prop"))
            )
            case other => Nil
        }

        // here, also multiple values can be given
        // convert string to enum (SearchComparisonOperatorV1), throw error if unknown
        val compop: Seq[SearchComparisonOperatorV1.Value] = params.get("compop") match {
            case Some(compopList: Seq[String]) => compopList.map(
                (compop: String) => {
                    SearchComparisonOperatorV1.lookup(compop)
                }
            )
            case other => Nil
        }

        // here, also multiple values can be given
        val searchval: Seq[String] = params.get("searchval") match {
            case Some(searchvalList: Seq[String]) => searchvalList // Attention: searchval cannot be processed (escaped) here because we do not know its value type yet
            case other => Nil
        }

        // propertyId, compop, and searchval are parallel structures (parallel arrays): they have to be of the same length
        // in case of "compop" set to "EXISTS", also "searchval" has to be given as a param with an empty value (parallel arrays)
        if (!((propertyIri.length == compop.length) && (compop.length == searchval.length))) {
            // invalid length of parallel param structure
            throw BadRequestException(s"propertyId, compop, and searchval are not given parallelly")
        }

        val showNRows: Int = params.get("show_nrows") match {
            case Some(showNRowsStrList: Seq[String]) =>
                val showNRowsVal = InputValidation.toInt(showNRowsStrList.head, () => throw BadRequestException(s"Can't parse integer parameter 'show_nrows' for extended search: $showNRowsStrList"))
                showNRowsVal match {
                    case -1 => defaultShowNRows
                    case _ => showNRowsVal
                }
            case None => defaultShowNRows
        }

        val startAt: Int = params.get("start_at") match {
            case Some(startAtStrList: Seq[String]) =>
                InputValidation.toInt(startAtStrList.head, () => throw BadRequestException(s"Can't parse integer parameter 'start_at' for extended search: $startAtStrList"))
            case None => 0
        }

        ExtendedSearchGetRequestV1(
            filterByRestype = restypeIri,
            filterByProject = projectIri,
            filterByOwner = ownerIri,
            propertyIri = propertyIri,
            compareProps = compop,
            searchValue = searchval, // not processed (escaped) yet
            userProfile = userProfile,
            showNRows = showNRows,
            startAt = startAt
        )
    }

    def makeFulltextSearchRequestMessage(userProfile: UserProfileV1, searchval: String, params: Map[String, String]): FulltextSearchGetRequestV1 = {

        params.get("searchtype") match {
            case Some("fulltext") => ()
            case other => throw BadRequestException(s"Unexpected searchtype param for fulltext search")
        }

        val restypeIri: Option[IRI] = params.get("filter_by_restype") match {
            case Some(restype: IRI) => Some(InputValidation.toIri(restype, () => throw BadRequestException(s"Unexpected param 'filter_by_restype' for extended search: $restype")))
            case other => None
        }
        val projectIri: Option[IRI] = params.get("filter_by_project") match {
            case Some(project: IRI) => Some(InputValidation.toIri(project, () => throw BadRequestException(s"Unexpected param 'filter_by_project' for extended search: $project")))
            case other => None
        }

        val searchString = InputValidation.toSparqlEncodedString(searchval)

        val showNRows: Int = params.get("show_nrows") match {
            case Some(showNRowsStr) =>
                val showNRowsVal = InputValidation.toInt(showNRowsStr, () => throw BadRequestException(s"Can't parse integer parameter 'show_nrows' for extended search: $showNRowsStr"))
                showNRowsVal match {
                    case -1 => defaultShowNRows
                    case _ => showNRowsVal
                }
            case None => defaultShowNRows
        }

        val startAt: Int = params.get("start_at") match {
            case Some(startAtStr) =>
                InputValidation.toInt(startAtStr, () => throw BadRequestException(s"Can't parse integer parameter 'start_at' for extended search: $startAtStr"))
            case None => 0
        }

        FulltextSearchGetRequestV1(
            searchValue = searchString, // save
            filterByRestype = restypeIri,
            filterByProject = projectIri,
            userProfile = userProfile,
            showNRows = showNRows,
            startAt = startAt
        )
    }

    def rapierPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "search" /) {
            // in the original API, there is a slash after "search": "http://www.salsah.org/api/search/?searchtype=extended"
            get {
                requestContext => {
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)
                        val params: Map[String, Seq[String]] = requestContext.request.uri.query.toMultiMap
                        makeExtendedSearchRequestMessage(userProfile, params)
                    }
                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            }
        } ~
            path("v1" / "search" / Segment) { searchval => // TODO: if a space is encoded as a "+", this is not converted back to a space
                get {
                    requestContext => {
                        val requestMessageTry = Try {
                            val userProfile = getUserProfileV1(requestContext)
                            val params: Map[String, String] = requestContext.request.uri.query.toMap
                            makeFulltextSearchRequestMessage(userProfile, searchval, params)
                        }
                        RouteUtilV1.runJsonRoute(
                            requestMessageTry,
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
