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

package org.knora.webapi.routing.v2

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives.{get, path, _}
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.knora.webapi.messages.v2.responder.listsmessages.ListsGetRequestV2
import org.knora.webapi.routing.{Authenticator, RouteUtilV2}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{BadRequestException, IRI, SettingsImpl}

import scala.collection.immutable
import scala.concurrent.ExecutionContextExecutor

/**
  * Provides a function for API routes that deal with search.
  */
object ListsRouteV2 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        implicit val materializer = ActorMaterializer()
        val responderManager = system.actorSelection("/user/responderManager")

        path("v2" / "lists" / Segments) { lIris: immutable.Seq[String] =>
            get {

                /* return a list (a graph with all list nodes) */
                requestContext =>
                    val requestingUser = getUserADM(requestContext)

                    val listIris: Seq[IRI] = lIris.map {
                        lIri: String =>
                            stringFormatter.validateAndEscapeIri(lIri, throw BadRequestException(s"Invalid list IRI: '$lIri'"))
                    }

                    val requestMessage = ListsGetRequestV2(listIris, requestingUser)

                    RouteUtilV2.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        RouteUtilV2.getOntologySchema(requestContext)
                    )

            }
        }

    }
}