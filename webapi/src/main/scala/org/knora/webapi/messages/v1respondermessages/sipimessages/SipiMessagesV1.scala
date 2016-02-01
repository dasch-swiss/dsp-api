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

package org.knora.webapi.messages.v1respondermessages.sipimessages

import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.usermessages.UserProfileV1
import org.knora.webapi.messages.v1respondermessages.valuemessages.FileValueV1
import org.knora.webapi.messages.v1respondermessages.{KnoraRequestV1, KnoraResponseV1}
import spray.json._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * Represents an binary file submitted to Knora API.
  *
  * @param originalFilename the original name of the binary file.
  * @param originalMimeType the MIME type of the binary file (e.g. image/tiff).
  * @param sourceTmpFilename the temporary location of the source binary file on disk.
  */
case class SipiBinaryFileRequestV1(originalFilename: String,
                                   originalMimeType: String,
                                   sourceTmpFilename: String,
                                   userProfile: UserProfileV1) extends SipiResponderRequestV1

/**
  * Response from SIPIResponder to a [[SipiBinaryFileRequestV1]] representing one or more [[FileValueV1]].
  *
  * @param fileValuesV1 a list of [[FileValueV1]]
  */
case class SipiBinaryFileResponseV1(fileValuesV1: Vector[FileValueV1])

/**
  * Represents the response received from SIPI after a conversion request.
  *
  * @param status status code returned by SIPI.
  * @param nx x dimension of the converted image.
  * @param ny y dimension of the converted image.
  */
case class SipiImageConversionResponse(status: Int, nx: Int, ny: Int, mimetype: String)

/**
  * An abstract trait representing a Knora v1 API request message that can be sent to `SipiResponderV1`.
  */
sealed trait SipiResponderRequestV1 extends KnoraRequestV1

/**
  * A Knora v1 API request message that requests information about a `FileValue`.
  *
  * @param fileValueIri the IRI of the file value to be queried.
  * @param userProfile the profile of the user making the request.
  */
case class SipiFileInfoGetRequestV1(fileValueIri: IRI, userProfile: UserProfileV1) extends SipiResponderRequestV1

/**
  * Represents the Knora API v1 JSON response to a request for a information about a `FileValue`.
  *
  * @param permissionCode a code representing the user's maximum permission on the file.
  * @param path the path to the file.
  */
case class SipiFileInfoGetResponseV1(permissionCode: Option[Int],
                                     path: Option[String]) extends KnoraResponseV1 {
    def toJsValue = RepresentationV1JsonProtocol.sipiFileInfoGetResponseV1Format.write(this)
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about representations of a resource.
  */
object RepresentationV1JsonProtocol extends DefaultJsonProtocol with NullOptions {

    implicit val sipiFileInfoGetResponseV1Format: RootJsonFormat[SipiFileInfoGetResponseV1] = jsonFormat2(SipiFileInfoGetResponseV1)
    implicit val sipiConversionResponseFormat = jsonFormat4(SipiImageConversionResponse)
}


