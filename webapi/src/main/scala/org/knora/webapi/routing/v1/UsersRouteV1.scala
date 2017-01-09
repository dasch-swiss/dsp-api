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

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.apache.commons.validator.routines.UrlValidator
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.routing.{Authenticator, RouteUtilV1}
import org.knora.webapi.util.InputValidation

/**
  * Provides a spray-routing function for API routes that deal with lists.
  */
object UsersRouteV1 extends Authenticator {

    /* bring json protocol into scope */
    import UserV1JsonProtocol._

    private val schemes = Array("http", "https")
    private val urlValidator = new UrlValidator(schemes)
    
    def rapierPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {


        implicit val system: ActorSystem = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "users" / "iri" / Segment) {value =>
            get {
                requestContext =>
                    val userIri = InputValidation.toIri(value, () => throw BadRequestException(s"Invalid user IRI $value"))
                    val requestMessage = UserProfileByIRIGetRequestV1(userIri, UserProfileType.RESTRICTED)
                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~
        path("v1" / "users" / "username" / Segment) {value =>
            get {
                requestContext =>
                    val requestMessage = UserProfileByUsernameGetRequestV1(value, UserProfileType.RESTRICTED)
                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~
        path("v1" / "users" / Segment) { value =>
            post {
                /* create a new user */
                entity(as[CreateUserApiRequestV1]) { apiRequest => requestContext =>
                    val userProfile = getUserProfileV1(requestContext)

                    val newUserData = NewUserDataV1(
                        username = apiRequest.username,
                        givenName = apiRequest.givenName,
                        familyName = apiRequest.familyName,
                        email = apiRequest.email,
                        password = apiRequest.password,
                        lang = apiRequest.lang)

                    val requestMessage = UserCreateRequestV1(
                        newUserData,
                        userProfile,
                        apiRequestID = UUID.randomUUID
                    )

                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            } ~ put {
                /* update an existing user */
                entity(as[UpdateUserApiRequestV1]) { apiRequest => requestContext =>

                    val userIri = InputValidation.toIri(value, () => throw BadRequestException(s"Invalid user IRI $value"))
                    val userProfile = getUserProfileV1(requestContext)

                    val requestMessage = UserUpdateRequestV1(
                        userIri = userIri,
                        propertyIri = apiRequest.propertyIri,
                        newValue = apiRequest.newValue,
                        userProfile,
                        apiRequestID = UUID.randomUUID
                    )
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
}
