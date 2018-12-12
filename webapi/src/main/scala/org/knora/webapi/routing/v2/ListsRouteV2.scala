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
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.listsmessages.{ListGetRequestV2, NodeGetRequestV2}
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_PATH
import org.knora.webapi.routing.{Authenticator, RouteUtilV2}
import org.knora.webapi.util.StringFormatter

import scala.concurrent.{ExecutionContext, Future}

/**
  * Provides a function for API routes that deal with lists and nodes.
  */
object ListsRouteV2 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
        implicit val timeout: Timeout = settings.defaultTimeout
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        val responderManager = system.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)

        path("v2" / "lists" / Segment) { lIri: String =>
            get {

                /* return a list (a graph with all list nodes) */
                requestContext =>
                    val requestMessage: Future[ListGetRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        listIri: IRI = stringFormatter.validateAndEscapeIri(lIri, throw BadRequestException(s"Invalid list IRI: '$lIri'"))
                    } yield ListGetRequestV2(listIri, requestingUser)

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        ApiV2WithValueObjects
                    )

            }
        } ~
        path("v2" / "node" / Segment) { nIri: String =>
            get {

                /* return a list (a graph with all list nodes) */
                requestContext =>
                    val requestMessage: Future[NodeGetRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        nodeIri: IRI = stringFormatter.validateAndEscapeIri(nIri, throw BadRequestException(s"Invalid list IRI: '$nIri'"))
                    } yield NodeGetRequestV2(nodeIri, requestingUser)

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log,
                        ApiV2WithValueObjects
                    )
            }
        }

    }
}
