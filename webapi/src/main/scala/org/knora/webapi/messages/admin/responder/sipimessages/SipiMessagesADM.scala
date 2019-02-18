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

package org.knora.webapi.messages.admin.responder.sipimessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectRestrictedViewSettingsADM, ProjectsADMJsonProtocol}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import spray.json.{DefaultJsonProtocol, JsValue, NullOptions, RootJsonFormat}

/**
  * An abstract trait representing a Knora v1 API request message that can be sent to `SipiResponderV2`.
  */
sealed trait SipiResponderRequestADM extends KnoraRequestV1

/**
  * A Knora v1 API request message that requests information about a `FileValue`.
  *
  * @param projectID   the project shortcode.
  * @param filename    the name of the file belonging to the file value to be queried.
  * @param requestingUser the profile of the user making the request.
  */
case class SipiFileInfoGetRequestADM(projectID: String, filename: String, requestingUser: UserADM) extends SipiResponderRequestADM

/**
  * Represents the Knora API v1 JSON response to a request for a information about a `FileValue`.
  *
  * @param permissionCode         a code representing the user's maximum permission on the file.
  * @param restrictedViewSettings the project's restricted view settings.
  */
case class SipiFileInfoGetResponseADM(permissionCode: Int,
                                      restrictedViewSettings: Option[ProjectRestrictedViewSettingsADM],
                                     ) extends KnoraResponseV1 {
    def toJsValue: JsValue = SipiResponderResponseADMJsonProtocol.sipiFileInfoGetResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about representations of a resource.
  */
object SipiResponderResponseADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions with ProjectsADMJsonProtocol {

    implicit val sipiFileInfoGetResponseADMFormat: RootJsonFormat[SipiFileInfoGetResponseADM] = jsonFormat2(SipiFileInfoGetResponseADM)
}
