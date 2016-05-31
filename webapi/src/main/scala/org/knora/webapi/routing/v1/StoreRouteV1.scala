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
import akka.util.Timeout
import org.knora.webapi.SettingsImpl
import org.knora.webapi.messages.v1.responder.storemessages.ResetTriplestoreContentRequestV1
import org.knora.webapi.messages.v1.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import spray.routing.Directives._
import spray.routing._

import scala.concurrent.duration._
import scala.util.Try


case class Test(path: String, name: String)

/**
  * A route used to serve data to CKAN. It is used be the Ckan instance running under http://data.humanities.ch.
  */
object StoreRouteV1 extends Authenticator {

    def rapierPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {

        import org.knora.webapi.messages.v1.store.triplestoremessages.TriplestoreJsonProtocol._
        import spray.httpx.SprayJsonSupport.sprayJsonUnmarshaller

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
            }
        } ~
        path("v1" / "store" / "ResetTriplestoreContent") {
            post {
                /* ResetTriplestoreContent */
                entity(as[Seq[RdfDataObject]]) { apiRequest => requestContext =>

                    val requestMessageTry = Try {
                        // create the message
                        ResetTriplestoreContentRequestV1(apiRequest)
                    }

                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
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
