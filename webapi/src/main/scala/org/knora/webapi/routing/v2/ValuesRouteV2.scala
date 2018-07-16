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

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV2}
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.jsonld.{JsonLDDocument, JsonLDUtil}

import scala.concurrent.{ExecutionContextExecutor, Future}
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_PATH

/**
  * Provides a routing function for API v2 routes that deal with values.
  */
object ValuesRouteV2 extends Authenticator {
    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        val responderManager = system.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)

        path("v2" / "values") {
            entity(as[String]) { jsonRequest =>
                requestContext => {
                    val requestDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(jsonRequest)

                    val requestMessageFuture: Future[CreateValueRequestV2] = for {
                        requestingUser <- getUserADM(requestContext)
                        requestMessage: CreateValueRequestV2 <- CreateValueRequestV2.fromJsonLD(
                            requestDoc,
                            apiRequestID = UUID.randomUUID,
                            requestingUser = requestingUser,
                            responderManager = responderManager,
                            log = log
                        )
                    } yield requestMessage

                    RouteUtilV2.runRdfRouteWithFuture(
                        requestMessageFuture,
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
}
