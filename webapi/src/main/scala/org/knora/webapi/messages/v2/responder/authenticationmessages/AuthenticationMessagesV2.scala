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

package org.knora.webapi.messages.v2.responder.authenticationmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.BadRequestException
import spray.json._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * Represents an API request payload that asks the Knora API server to authenticate the user and create a JWT token
  *
  * @param email    the user's email.
  * @param password the user's password.
  */
case class LoginApiRequestPayloadV2(email: String,
                                    password: String) {

    // email and password need to be supplied
    if (email.isEmpty || password.isEmpty) throw BadRequestException("Both email and password need to be supplied.")
}

/**
  * Represents credentials that a user can supply.
  *
  * @param email    the optionally supplied email.
  * @param password the optionally supplied password.
  * @param token    the optionally supplied json web token.
  */
case class KnoraCredentialsV2(email: Option[String] = None,
                              password: Option[String] = None,
                              token: Option[String] = None) {
    def isEmpty: Boolean = this.email.isEmpty && this.password.isEmpty && this.token.isEmpty
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v2 JSON for property values.
  */
trait AuthenticationV2JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {

    implicit val loginApiRequestPayloadV2Format: RootJsonFormat[LoginApiRequestPayloadV2] = jsonFormat2(LoginApiRequestPayloadV2)
}