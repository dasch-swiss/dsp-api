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

package org.knora.webapi.routing.admin

import akka.actor.{ActorSelection, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import io.swagger.annotations.Api
import javax.ws.rs.Path
import org.knora.webapi.SettingsImpl
import org.knora.webapi.messages.admin.responder.storesmessages.{ResetTriplestoreContentRequestADM, StoresADMJsonProtocol}
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.{Authenticator, RouteUtilADM}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

/**
  * A route used to send requests which can directly affect the data stored inside the triplestore.
  */

@Api(value = "store", produces = "application/json")
@Path("/admin/store")
class StoreRouteADM(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter) extends Authenticator with StoresADMJsonProtocol {

    implicit val system: ActorSystem = _system
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = Timeout(300.seconds)
    val responderManager: ActorSelection = system.actorSelection("/user/responderManager")

    def knoraApiPath = Route {

        path("admin" / "store") {
            get {
                requestContext =>

                    /** Maybe return some statistics about the store, e.g., what triplestore, number of triples in
                      * each named graph and in total, etc.
                      */
                    // TODO: Implement some simple return
                    requestContext.complete("Hello World")
            }
        } ~ path("admin" / "store" / "ResetTriplestoreContent") {
            post {
                /* ResetTriplestoreContent */
                entity(as[Seq[RdfDataObject]]) { apiRequest =>
                    requestContext =>
                        val requestMessage = for {
                            requestingUser <- getUserADM(requestContext)
                        } yield ResetTriplestoreContentRequestADM(apiRequest)

                        RouteUtilADM.runJsonRoute(
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
