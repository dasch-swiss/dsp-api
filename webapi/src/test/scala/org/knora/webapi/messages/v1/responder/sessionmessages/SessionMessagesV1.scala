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

package org.knora.webapi.messages.v1.responder.sessionmessages

import spray.json.DefaultJsonProtocol

/**
  * Represents a response Knora returns when communicating with the 'v1/session' route during the 'login' operation.
  *
  * @param status is the returned status code.
  * @param message is the returned message.
  * @param sid is the returned session id.
  */
case class SessionResponse(status: Int, message: String, sid: String)

/**
  * A spray-json protocol used for turning the JSON responses from the 'login' operation during communication with the
  * 'v1/session' route into a case classes for easier testing.
  */
trait SessionJsonProtocol extends DefaultJsonProtocol {
    implicit val SessionResponseFormat = jsonFormat3(SessionResponse.apply)
}
