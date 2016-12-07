/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
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
import org.knora.webapi.messages.v1.responder.storemessages.{ResetTriplestoreContentRequestV1, StoreV1JsonProtocol}
import org.knora.webapi.messages.v1.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * A route used to send requests which can directly affect the data stored inside the triplestore.
  */
object StoreRouteV1 extends Authenticator with StoreV1JsonProtocol {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter) = Route {

        implicit val system: ActorSystem = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = Timeout(300.seconds)
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "store") {
            get {
                requestContext =>
                    /** Maybe return some statistics about the store, e.g., what triplestore, number of triples in
                      * each named graph and in total, etc.
                      */
                    // TODO: Implement some simple return
                    requestContext.complete("Hello World")
            }
        } ~ path("v1" / "store" / "ResetTriplestoreContent") {
            post {
                /* ResetTriplestoreContent */
                entity(as[Seq[RdfDataObject]]) { apiRequest =>
                    requestContext =>
                        val requestMessage = ResetTriplestoreContentRequestV1(apiRequest)

                        RouteUtilV1.runJsonRoute(
                            Future(requestMessage),
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
