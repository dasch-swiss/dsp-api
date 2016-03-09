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

package org.knora.webapi.routing.v1

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import org.apache.commons.validator.routines.UrlValidator
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.usermessages.UserV1JsonProtocol._
import org.knora.webapi.messages.v1respondermessages.usermessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import spray.routing.Directives._
import spray.routing._

import scala.util.Try

/**
  * Provides a spray-routing function for API routes that deal with lists.
  */
object UsersRouteV1 extends Authenticator {

    private val schemes = Array("http", "https")
    private val urlValidator = new UrlValidator(schemes)

    def rapierPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system: ActorSystem = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "users" / Segment) { value =>
            get {
                requestContext =>
                    val requestMessageTry = Try {
                        if (urlValidator.isValid(value)) {
                            /* valid URL */
                            UserProfileGetRequestV1(value, true)
                        } else {
                            /* not valid URL so I assume it is an username */
                            UserProfileByUsernameGetRequestV1(value, true)
                        }
                    }
                    RouteUtilV1.runJsonRoute(
                        requestMessageTry,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            } ~ post {
                /* create a new user */
                entity(as[CreateUserApiRequestV1]) { apiRequest => requestContext =>
                    val requestMessageTry = Try {
                        val userProfile = getUserProfileV1(requestContext)

                        val newUserData = NewUserDataV1(apiRequest.username,
                            apiRequest.givenName,
                            apiRequest.familyName,
                            apiRequest.email,
                            apiRequest.password,
                            apiRequest.lang,
                            apiRequest.projects)

                        UserCreateRequestV1(newUserData, userProfile)
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
