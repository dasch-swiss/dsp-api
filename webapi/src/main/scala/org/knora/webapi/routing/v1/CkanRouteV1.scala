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
import org.knora.webapi.messages.v1.responder.ckanmessages.CkanRequestV1
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.{KnoraDispatchers, SettingsImpl}

import scala.concurrent.ExecutionContext

/**
  * A route used to serve data to CKAN. It is used be the Ckan instance running under http://data.humanities.ch.
  */
object CkanRouteV1 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {

        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
        implicit val timeout: Timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "ckan") {
            get {
                requestContext =>

                    val requestMessage = for {
                        userProfile <- getUserADM(requestContext)
                        params = requestContext.request.uri.query().toMap
                        project: Option[Seq[String]] = params.get("project").map(_.split(","))
                        limit: Option[Int] = params.get("limit").map(_.toInt)
                        info: Boolean = params.getOrElse("info", false) == true
                    } yield CkanRequestV1(project, limit, info, userProfile)

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
