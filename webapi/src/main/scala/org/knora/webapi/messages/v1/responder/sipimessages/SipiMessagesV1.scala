/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.v1.responder.sipimessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import spray.json.{DefaultJsonProtocol, NullOptions, RootJsonFormat}

/**
  * An abstract trait representing a Knora v1 API request message that can be sent to `SipiResponderV1`.
  */
sealed trait SipiResponderRequestV1 extends KnoraRequestV1

/**
  * A Knora v1 API request message that requests information about a `FileValue`.
  *
  * @param filename    the name of the file belonging to the file value to be queried.
  * @param userProfile the profile of the user making the request.
  */
case class SipiFileInfoGetRequestV1(filename: String, userProfile: UserProfileV1) extends SipiResponderRequestV1

/**
  * Represents the Knora API v1 JSON response to a request for a information about a `FileValue`.
  *
  * @param permissionCode a code representing the user's maximum permission on the file.
  */
case class SipiFileInfoGetResponseV1(permissionCode: Int) extends KnoraResponseV1 {
    def toJsValue = RepresentationV1JsonProtocol.sipiFileInfoGetResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about representations of a resource.
  */
object RepresentationV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit val sipiFileInfoGetResponseV1Format: RootJsonFormat[SipiFileInfoGetResponseV1] = jsonFormat1(SipiFileInfoGetResponseV1)
}
