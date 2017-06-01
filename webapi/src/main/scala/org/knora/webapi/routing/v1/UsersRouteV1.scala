/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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
import org.knora.webapi.routing.{KnoraSecurityDirectives, _}
import org.knora.webapi.util.InputValidation

/**
  * Provides a spray-routing function for API routes that deal with lists.
  */
object UsersRouteV1 extends KnoraSecurityDirectives {

    /* bring json protocol into scope */
    import UserV1JsonProtocol._

    private val schemes = Array("http", "https")
    private val urlValidator = new UrlValidator(schemes)

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {


        implicit val system: ActorSystem = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout
        val responderManager = system.actorSelection("/user/responderManager")

        path("v1" / "users" / "iri" / Segment) {value =>
            get {
                requestContext => authenticate[UserProfileV1] { userProfile =>
                    //val userProfile = getUserProfileV1(requestContext)
                    val userIri = InputValidation.toIri(value, () => throw BadRequestException(s"Invalid user IRI $value"))
                    val requestMessage = UserProfileByIRIGetRequestV1(userIri, UserProfileType.RESTRICTED, userProfile)
                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
                }
            }
        } ~
        path("v1" / "users" / "email" / Segment) {value =>
            get {
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val requestMessage = UserProfileByEmailGetRequestV1(value, UserProfileType.RESTRICTED, userProfile)
                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        } ~
        path("v1" / "users") {
            get {
                requestContext =>
                    val userProfile = getUserProfileV1(requestContext)
                    val requestMessage = UsersGetRequestV1(userProfile)
                    RouteUtilV1.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            } ~  post {
                /* create a new user */
                entity(as[CreateUserApiRequestV1]) { apiRequest => requestContext =>
                    val userProfile = getUserProfileV1(requestContext)

                    val requestMessage = UserCreateRequestV1(
                        createRequest = apiRequest,
                        userProfile,
                        apiRequestID = UUID.randomUUID()
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
        } ~
        path("v1" / "users" / Segment) { value =>
            put {
                /* update existing user's information */
                entity(as[UpdateUserApiRequestV1]) { apiRequest => requestContext =>

                    val userIri = InputValidation.toIri(value, () => throw BadRequestException(s"Invalid user IRI $value"))
                    val userProfile = getUserProfileV1(requestContext)

                    val requestMessage = UserUpdateRequestV1(
                        userIri = userIri,
                        updateRequest = apiRequest,
                        userProfile,
                        apiRequestID = UUID.randomUUID()
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
                /* update existing user's password */
                entity(as[ChangeUserPasswordApiRequestV1]) { apiRequest => requestContext =>

                    val userIri = InputValidation.toIri(value, () => throw BadRequestException(s"Invalid user IRI $value"))
                    val userProfile = getUserProfileV1(requestContext)

                    val requestMessage = UserChangePasswordRequestV1(
                        userIri = userIri,
                        changePasswordRequest = apiRequest,
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
                /* update existing user's status */
                entity(as[ChangeUserStatusApiRequestV1]) { apiRequest => requestContext =>

                    val userIri = InputValidation.toIri(value, () => throw BadRequestException(s"Invalid user IRI $value"))
                    val userProfile = getUserProfileV1(requestContext)

                    val requestMessage = UserChangeStatusRequestV1(
                        userIri = userIri,
                        changeStatusRequest = apiRequest,
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
