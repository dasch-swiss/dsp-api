/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData}

/**
  * A route providing authentication support. It allows the creation of "sessions", which is used in the SALSAH app.
  */
class AuthenticationRouteV1(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

    def knoraApiPath: Route = {

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