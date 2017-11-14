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

package org.knora.webapi.routing.admin

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.knora.webapi.messages.admin.responder.listadminmessages.{ListGetAdminRequest, ListNodeInfoGetAdminRequest, ListsGetAdminRequest}
import org.knora.webapi.routing.{Authenticator, RouteUtilAdmin}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{BadRequestException, IRI, SettingsImpl}

import scala.concurrent.ExecutionContextExecutor

/**
  * Provides a spray-routing function for API routes that deal with lists.
  */
object ListsAdminRoute extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")
        val stringFormatter = StringFormatter.getInstance

        path("admin" / "lists") {
            get {
                /* return all lists */
                parameters("projectIri".?) { projectIri: Option[IRI] =>
                    requestContext =>
                        val userProfile = getUserProfileV1(requestContext)

                        val requestMessage = ListsGetAdminRequest(projectIri, userProfile)

                        RouteUtilAdmin.runJsonRoute(
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
        path("admin" / "lists" / Segment) {iri =>
            get {
                /* return a list (a graph with all list nodes) */
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val listIri = stringFormatter.toIri(iri, () => throw BadRequestException(s"Invalid param list IRI: $iri"))

                    val requestMessage = ListGetAdminRequest(listIri, userProfile)

                    RouteUtilAdmin.runJsonRoute(
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
        path("admin" / "lists" / "nodes" / Segment) {iri =>
            get {
                /* return a single list node */
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val listIri = stringFormatter.toIri(iri, () => throw BadRequestException(s"Invalid param list IRI: $iri"))

                    val requestMessage = ListNodeInfoGetAdminRequest(listIri, userProfile)

                    RouteUtilAdmin.runJsonRoute(
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
