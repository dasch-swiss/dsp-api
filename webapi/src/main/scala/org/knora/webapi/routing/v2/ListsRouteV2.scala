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
import org.knora.webapi.messages.v2.responder.listmessages.{ListExtendedGetRequestV2, ListNodeInfoGetRequestV2, ListsGetRequestV2}
import org.knora.webapi.routing.{Authenticator, RouteUtilV2}
import org.knora.webapi.util.InputValidation
import org.knora.webapi.{BadRequestException, IRI, SettingsImpl}

/**
  * Provides a spray-routing function for API routes that deal with lists.
  */
object ListsRouteV2 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v2" / "lists") {
            get {
                /* return all lists */
                parameters("projectIri".?) { projectIri: Option[IRI] =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)

                        val requestMessage = ListsGetRequestV2(projectIri, userProfile)

                        RouteUtilV2.runJsonRoute(
                            requestMessage,
                            requestContext,
                            settings,
                            responderManager,
                            log
                        )
                }
            } ~
            post {
                /* create a list */
                ???
            }
        } ~
        path("v2" / "lists" / Segment) {iri =>
            get {
                /* return a list */
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val listIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid param list IRI: $iri"))

                    val requestMessage = ListExtendedGetRequestV2(listIri, userProfile)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            } ~
            put {
                /* update list */
                ???
            } ~
            delete {
                /* delete (deactivate) list */
                ???
            }
        } ~
        path("v2" / "lists" / "nodes" / Segment) {iri =>
            get {
                /* return a list node */
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val listIri = InputValidation.toIri(iri, () => throw BadRequestException(s"Invalid param list IRI: $iri"))

                    val requestMessage = ListNodeInfoGetRequestV2(listIri, userProfile)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            } ~
            put {
                /* update list node */
                ???
            } ~
            delete {
                /* delete list node */
                ???
            }
        }
    }
}
