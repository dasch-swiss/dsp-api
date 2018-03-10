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
import org.knora.webapi.SettingsImpl
import org.knora.webapi.routing.Authenticator

import scala.concurrent.ExecutionContextExecutor

/**
  * A route providing authentication support. It allows the creation of "sessions", which is used in the SALSAH app.
  */
object AuthenticationRouteV1 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout

        path("v1" / "authenticate") {
            get {
                requestContext => {
                    requestContext.complete {
                        doAuthenticateV1(requestContext)
                    }
                }
            }
        } ~ path("v1" / "session") {
            get {
                requestContext => {
                    requestContext.complete {
                        val params = requestContext.request.uri.query().toMap
                        if (params.contains("logout")) {
                            doLogoutV2(requestContext)
                        } else if (params.contains("login")) {
                            doLoginV1(requestContext)
                        } else {
                            doAuthenticateV1(requestContext)
                        }
                    }
                }
            } ~ post {
                requestContext => {
                    requestContext.complete {
                        doLoginV1(requestContext)
                    }
                }
            } ~ delete {
                requestContext => {
                    requestContext.complete {
                        doLogoutV2(requestContext)
                    }
                }
            }
        }
    }
}