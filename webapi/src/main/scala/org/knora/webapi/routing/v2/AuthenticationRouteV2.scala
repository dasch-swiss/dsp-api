/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.SettingsImpl
import org.knora.webapi.messages.v2.routing.authenticationmessages.{AuthenticationV2JsonProtocol, KnoraCredentialsV2, LoginApiRequestPayloadV2}
import org.knora.webapi.routing.Authenticator

/**
  * A route providing authentication support. It allows the creation of "sessions", which is used in the SALSAH app.
  */
object AuthenticationRouteV2 extends Authenticator with AuthenticationV2JsonProtocol {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout

        path("v2" / "authentication") {
            get { // authenticate credentials
                requestContext => {
                    requestContext.complete {
                        doAuthenticateV2(requestContext)
                    }
                }
            } ~
            post { // login
                /* send email, password in body as: {"email": "usersemail", "password": "userspassword"}
                 * returns a JWT token, which can be supplied with every request thereafter in the authorization
                 * header with the bearer scheme: 'Authorization: Bearer abc.def.ghi'
                 */
                entity(as[LoginApiRequestPayloadV2]) { apiRequest => requestContext =>
                    requestContext.complete {
                        doLoginV2(KnoraCredentialsV2(email = Some(apiRequest.email), password = Some(apiRequest.password)))
                    }
                }
            } ~
            delete { // logout
                requestContext =>
                    requestContext.complete {
                        doLogoutV2(requestContext)
                    }
            }
        }
    }
}