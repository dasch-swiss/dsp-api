/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.searchmessages.{ExtendedSearchGetRequestV1, FulltextSearchGetRequestV1, SearchComparisonOperatorV1}
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{BadRequestException, IRI, KnoraDispatchers, SettingsImpl}

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps

// slash after path without following segment

/**
  * Provides a spray-routing function for API routes that deal with search.
  */
object SearchRouteV1 extends Authenticator {

    /**
      * The default number of rows to show in search results.
      */
    private val defaultShowNRows = 25

    def makeExtendedSearchRequestMessage(userADM: UserADM, reverseParams: Map[String, Seq[String]]): ExtendedSearchGetRequestV1 = {
        val stringFormatter = StringFormatter.getGeneralInstance

        // Spray returns the parameters in reverse order, so reverse them before processing, because the JavaScript GUI expects the order to be preserved.
        val params = reverseParams.map {
            case (key, value) => key -> value.reverse
        }

        //println(params)

        params.get("searchtype") match {
            case Some(List("extended")) => ()
            case other => throw BadRequestException(s"Unexpected searchtype param for extended search: $other")
        }

        // only one value is expected
        val restypeIri: Option[IRI] = params.get("filter_by_restype") match {
            case Some(List(restype: IRI)) => Some(stringFormatter.validateAndEscapeIri(restype, throw BadRequestException(s"Value for param 'filter_by_restype' for extended search $restype is not a valid IRI. Please make sure that it was correctly URL encoded.")))
            case other => None
        }

        // only one value is expected
        val projectIri: Option[IRI] = params.get("filter_by_project") match {
            case Some(List(project: IRI)) => Some(stringFormatter.validateAndEscapeIri(project, throw BadRequestException(s"Value for param 'filter_by_project' for extended search $project is not a valid IRI. Please make sure that it was correctly URL encoded.")))
            case other => None
        }

        // only one value is expected
        val ownerIri: Option[IRI] = params.get("filter_by_owner") match {
            case Some(List(owner: IRI)) => Some(stringFormatter.validateAndEscapeIri(owner, throw BadRequestException(s"Value for param 'filter_by_owner' for extended search $owner is not a valid IRI. Please make sure that it was correctly URL encoded.")))
            case other => None
        }

        // here, also multiple values can be given
        val propertyIri: Seq[IRI] = params.get("property_id") match {
            case Some(propertyList: Seq[IRI]) => propertyList.map(
                prop => stringFormatter.validateAndEscapeIri(prop, throw BadRequestException(s"Value for param 'property_id' for extended search $prop is not a valid IRI. Please make sure that it was correctly URL encoded."))
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
                val showNRowsVal = stringFormatter.validateInt(showNRowsStrList.head, throw BadRequestException(s"Can't parse integer parameter 'show_nrows' for extended search: $showNRowsStrList"))
                showNRowsVal match {
                    case -1 => defaultShowNRows
                    case _ => showNRowsVal
                }
            case None => defaultShowNRows
        }

        val startAt: Int = params.get("start_at") match {
            case Some(startAtStrList: Seq[String]) =>
                stringFormatter.validateInt(startAtStrList.head, throw BadRequestException(s"Can't parse integer parameter 'start_at' for extended search: $startAtStrList"))
            case None => 0
        }

        ExtendedSearchGetRequestV1(
            filterByRestype = restypeIri,
            filterByProject = projectIri,
            filterByOwner = ownerIri,
            propertyIri = propertyIri,
            compareProps = compop,
            searchValue = searchval, // not processed (escaped) yet
            userProfile = userADM,
            showNRows = showNRows,
            startAt = startAt
        )
    }

    def makeFulltextSearchRequestMessage(userADM: UserADM, searchval: String, params: Map[String, String]): FulltextSearchGetRequestV1 = {
        val stringFormatter = StringFormatter.getGeneralInstance

        params.get("searchtype") match {
            case Some("fulltext") => ()
            case other => throw BadRequestException(s"Unexpected searchtype param for fulltext search")
        }

        val restypeIri: Option[IRI] = params.get("filter_by_restype") match {
            case Some(restype: IRI) => Some(stringFormatter.validateAndEscapeIri(restype, throw BadRequestException(s"Unexpected param 'filter_by_restype' for extended search: $restype")))
            case other => None
        }
        val projectIri: Option[IRI] = params.get("filter_by_project") match {
            case Some(project: IRI) => Some(stringFormatter.validateAndEscapeIri(project, throw BadRequestException(s"Unexpected param 'filter_by_project' for extended search: $project")))
            case other => None
        }

        val searchString = stringFormatter.toSparqlEncodedString(searchval, throw BadRequestException(s"Invalid search string: '$searchval'"))

        val showNRows: Int = params.get("show_nrows") match {
            case Some(showNRowsStr) =>
                val showNRowsVal = stringFormatter.validateInt(showNRowsStr, throw BadRequestException(s"Can't parse integer parameter 'show_nrows' for extended search: $showNRowsStr"))
                showNRowsVal match {
                    case -1 => defaultShowNRows
                    case _ => showNRowsVal
                }
            case None => defaultShowNRows
        }

        val startAt: Int = params.get("start_at") match {
            case Some(startAtStr) =>
                stringFormatter.validateInt(startAtStr, throw BadRequestException(s"Can't parse integer parameter 'start_at' for extended search: $startAtStr"))
            case None => 0
        }

        FulltextSearchGetRequestV1(
            searchValue = searchString, // save
            filterByRestype = restypeIri,
            filterByProject = projectIri,
            userProfile = userADM,
            showNRows = showNRows,
            startAt = startAt
        )
    }

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContextExecutor = system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)
        implicit val timeout: Timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "search" /) {
            // in the original API, there is a slash after "search": "http://www.salsah.org/api/search/?searchtype=extended"
            get {
                requestContext => {
                    val requestMessage = for {
                        userADM <- getUserADM(requestContext)
                        params: Map[String, Seq[String]] = requestContext.request.uri.query().toMultiMap
                    } yield makeExtendedSearchRequestMessage(userADM, params)

                    RouteUtilV1.runJsonRouteWithFuture(
                        requestMessage,
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
                    val requestMessage = for {
                        userADM <- getUserADM(requestContext)
                        params: Map[String, String] = requestContext.request.uri.query().toMap
                    } yield makeFulltextSearchRequestMessage(userADM, searchval, params)

                    RouteUtilV1.runJsonRouteWithFuture(
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
