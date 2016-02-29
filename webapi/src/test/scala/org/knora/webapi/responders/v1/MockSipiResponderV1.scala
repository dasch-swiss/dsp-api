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

package org.knora.webapi.responders.v1

import org.knora.webapi.messages.v1respondermessages.sipimessages._
import org.knora.webapi.messages.v1respondermessages.valuemessages.StillImageFileValueV1
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.InputValidation

import scala.concurrent.Future

/**
  * Takes the place of [[SipiResponderV1]] for tests without an actual Sipi server, by returning hard-coded responses
  * simulating responses from Sipi.
  */
class MockSipiResponderV1 extends ResponderV1 {
    /**
      * Imitates the Sipi server by returning a [[SipiResponderConversionResponseV1]] representing an image conversion request.
      *
      * @param conversionRequest the conversion request to be handled.
      * @return a [[SipiResponderConversionResponseV1]] imitating the answer from Sipi.
      */
    private def imageConversionResponse(conversionRequest: SipiResponderConversionRequestV1): Future[SipiResponderConversionResponseV1] = {

        // delete tmp file (depending on the kind of request given: only necessary if Knora stored the file - non GUI-case)
        def deleteTmpFile(conversionRequest: SipiResponderConversionRequestV1): Unit = {
            conversionRequest match {
                case (conversionPathRequest: SipiResponderConversionPathRequestV1) =>
                    // a tmp file has been created by the resources route (non GUI-case), delete it
                    InputValidation.deleteFileFromTmpLocation(conversionPathRequest.source)
                case _ => ()
            }
        }

        val originalFilename = conversionRequest.originalFilename
        val originalMimeType: String = conversionRequest.originalMimeType

        val fileValuesV1 = Vector(StillImageFileValueV1(// full representation
            internalMimeType = "image/jp2",
            originalFilename = originalFilename,
            originalMimeType = Some(originalMimeType),
            dimX = 800,
            dimY = 800,
            internalFilename = "full.jp2",
            qualityLevel = 100,
            qualityName = Some("full")
        ),
            StillImageFileValueV1(// thumbnail representation
                internalMimeType = "image/jpeg",
                originalFilename = originalFilename,
                originalMimeType = Some(originalMimeType),
                dimX = 80,
                dimY = 80,
                internalFilename = "thumb.jpg",
                qualityLevel = 10,
                qualityName = Some("thumbnail"),
                isPreview = true
            ))

        deleteTmpFile(conversionRequest)

        Future(SipiResponderConversionResponseV1(fileValuesV1, file_type = SipiConstants.FileType.IMAGE))
    }

    def receive = {
        case sipiResponderConversionFileRequest: SipiResponderConversionFileRequestV1 => future2Message(sender(), imageConversionResponse(sipiResponderConversionFileRequest), log)
        case sipiResponderConversionPathRequest: SipiResponderConversionPathRequestV1 => future2Message(sender(), imageConversionResponse(sipiResponderConversionPathRequest), log)
    }
}
