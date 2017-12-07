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

package org.knora.webapi.routing.v1

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.knora.webapi.SettingsImpl
import org.knora.webapi.messages.v1.responder.ckanmessages.CkanRequestV1
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * A route used to serve data to CKAN. It is used be the Ckan instance running under http://data.humanities.ch.
  */
object CkanRouteV1 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {

        implicit val system: ActorSystem = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = Timeout(30.seconds)
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "ckan") {
            get {
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val params = requestContext.request.uri.query().toMap
                    val project: Option[Seq[String]] = params.get("project").map(_.split(","))
                    val limit: Option[Int] = params.get("limit").map(_.toInt)
                    val info: Boolean = params.getOrElse("info", false) == true
                    val requestMessage = CkanRequestV1(project, limit, info, userProfile)

                    RouteUtilV1.runJsonRoute(
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
