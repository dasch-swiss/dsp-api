/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import java.io.File

import org.knora.webapi.BadRequestException
import org.knora.webapi.messages.v1.responder.sipimessages._
import org.knora.webapi.messages.v1.responder.valuemessages.StillImageFileValueV1
import org.knora.webapi.responders.ActorBasedResponder
import org.knora.webapi.util.ActorUtil._

import scala.concurrent.Future

/**
  * Keep track of the temporary files that was written in the route
  * when submitting a multipart request
  */
object SourcePath {
    private var sourcePath: File = new File("") // for init

    def setSourcePath(path: File) = {
        sourcePath = path
    }

    def getSourcePath() = {
        sourcePath
    }
}

/**
  * Takes the place of [[SipiResponderV1]] for tests without an actual Sipi server, by returning hard-coded responses
  * simulating responses from Sipi.
  */
class MockSipiResponderV1 extends ActorBasedResponder {
    /**
      * Imitates the Sipi server by returning a [[SipiResponderConversionResponseV1]] representing an image conversion request.
      *
      * @param conversionRequest the conversion request to be handled.
      * @return a [[SipiResponderConversionResponseV1]] imitating the answer from Sipi.
      */
    private def imageConversionResponse(conversionRequest: SipiResponderConversionRequestV1): Future[SipiResponderConversionResponseV1] = {
        Future {
            val originalFilename = conversionRequest.originalFilename
            val originalMimeType: String = conversionRequest.originalMimeType

            // we expect original mimetype to be "image/jpeg"
            if (originalMimeType != "image/jpeg") throw BadRequestException("Wrong mimetype for jpg file")

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

            // Whenever Knora had to create a temporary file, store its path
            // the calling test context can then make sure that is has actually been deleted after the test is done
            // (on successful or failed conversion)
            conversionRequest match {
                case conversionPathRequest: SipiResponderConversionPathRequestV1 =>
                    // store path to tmp file
                    SourcePath.setSourcePath(conversionPathRequest.source)
                case _ => () // params request only
            }

            SipiResponderConversionResponseV1(fileValuesV1, file_type = SipiConstants.FileType.IMAGE)
        }
    }

    def receive = {
        case sipiResponderConversionFileRequest: SipiResponderConversionFileRequestV1 => future2Message(sender(), imageConversionResponse(sipiResponderConversionFileRequest), log)
        case sipiResponderConversionPathRequest: SipiResponderConversionPathRequestV1 => future2Message(sender(), imageConversionResponse(sipiResponderConversionPathRequest), log)
    }
}
