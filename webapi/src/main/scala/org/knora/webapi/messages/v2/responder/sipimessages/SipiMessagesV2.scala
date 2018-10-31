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

package org.knora.webapi.messages.v2.responder.sipimessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.KnoraRequestV2
import spray.json._

/**
  * An abstract trait for messages that can be sent to [[org.knora.webapi.responders.v2.SipiResponderV2]].
  */
sealed trait SipiResponderRequestV2 extends KnoraRequestV2 {
    def requestingUser: UserADM
}

/**
  * Requests file metadata from Sipi. A successful response is a [[GetImageMetadataResponseV2]].
  *
  * @param fileUrl        the URL at which Sipi can serve the file.
  * @param requestingUser the user making the request.
  */
case class GetImageMetadataRequestV2(fileUrl: String,
                                     requestingUser: UserADM) extends SipiResponderRequestV2


/**
  * Represents a response from Sipi providing metadata about an image file.
  *
  * @param originalFilename the image's original filename.
  * @param originalMimeType the image's original MIME type.
  * @param width            the image's width in pixels.
  * @param height           the image's height in pixels.
  */
case class GetImageMetadataResponseV2(originalFilename: String,
                                      originalMimeType: String,
                                      width: Int,
                                      height: Int)

object GetImageMetadataResponseV2JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val getImageMetadataResponseV2Format: RootJsonFormat[GetImageMetadataResponseV2] = jsonFormat4(GetImageMetadataResponseV2)
}

/**
  * Asks Sipi to move a file from temporary to permanent storage.
  *
  * @param internalFilename the name of the file.
  * @param requestingUser   the user making the request.
  */
case class MoveTemporaryFileToPermanentStorageRequestV2(internalFilename: String,
                                                        requestingUser: UserADM) extends SipiResponderRequestV2