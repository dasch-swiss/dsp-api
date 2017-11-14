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

package org.knora.webapi.messages.v2.routing.authenticationmessages

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
  * An abstract knora credentials class.
  */
sealed abstract class KnoraCredentialsV2()

/**
  * Represents email/password credentials that a user can supply within the authorization header or as URL parameters.
  *
  * @param email    the supplied email.
  * @param password the supplied password.
  */
case class KnoraPasswordCredentialsV2(email: String, password: String) extends KnoraCredentialsV2

/**
  * Represents token credentials that a user can supply withing the authorization header or as URL parameters.
  *
  * @param token the supplied json web token.
  */
case class KnoraTokenCredentialsV2(token: String) extends KnoraCredentialsV2


/**
  * Represents session credentials that a user can supply within the cookie header.
  *
  * @param token the supplied session token.
  */
case class KnoraSessionCredentialsV2(token: String) extends KnoraCredentialsV2


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v2 JSON for property values.
  */
trait AuthenticationV2JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {

    implicit val loginApiRequestPayloadV2Format: RootJsonFormat[LoginApiRequestPayloadV2] = jsonFormat2(LoginApiRequestPayloadV2)
}