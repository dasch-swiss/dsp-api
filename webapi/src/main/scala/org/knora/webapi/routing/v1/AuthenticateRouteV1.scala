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
import org.knora.webapi.{IRI, SettingsImpl}
import org.knora.webapi.routing.Authenticator

/**
  * A route providing authentication support. It allows the creation of "sessions", which is used in the SALSAH app.
  */
object AuthenticateRouteV1 extends Authenticator {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout

        path("v1" / "authenticate") {
            get {
                requestContext => {
                    requestContext.complete {
                        doAuthenticate(requestContext)
                    }
                }
            }
        } ~ path("v1" / "session") {
            get {
                parameters("login"?, "logout".?) { (login: Option[String], logout: Option[String]) =>
                    requestContext => {
                        requestContext.complete {
                            if (login.isDefined) {
                                doLogin(requestContext)
                            } else if (logout.isDefined) {
                                doLogout(requestContext)
                            } else {
                                doSessionAuthentication(requestContext)
                            }
                        }
                    }
                }
            } ~ post {
                requestContext => {
                    requestContext.complete {
                        doLogin(requestContext)
                    }
                }
            } ~ delete {
                requestContext => {
                    requestContext.complete {
                        doLogout(requestContext)
                    }
                }
            }
        }
    }
}