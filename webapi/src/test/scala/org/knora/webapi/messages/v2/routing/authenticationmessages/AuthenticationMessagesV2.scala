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

package org.knora.webapi.messages.v1.responder.sessionmessages

import spray.json.DefaultJsonProtocol

/**
  * Represents a response Knora returns when communicating with the 'v2/authentication' route during the 'login' operation.
  *
  * @param token is the returned json web token.
  */
case class LoginResponse(token: String)

/**
  * A spray-json protocol used for turning the JSON responses from the 'login' operation during communication with the
  * 'v2/authentication' route into a case classes for easier testing.
  */
trait AuthenticationV2JsonProtocol extends DefaultJsonProtocol {
    implicit val SessionResponseFormat = jsonFormat1(LoginResponse.apply)
}
