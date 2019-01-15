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

package org.knora.webapi.responders.v2

import org.knora.webapi.SipiException
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.sipimessages._
import org.knora.webapi.responders.ActorBasedResponder
import org.knora.webapi.util.ActorUtil.{handleUnexpectedMessage, try2Message}

import scala.util.{Failure, Success, Try}

/**
  * Constants for [[MockSipiResponderV2]].
  */
object MockSipiResponderV2 {
    /**
      * A request to [[MockSipiResponderV2]] with this filename will always cause the responder to simulate a Sipi
      * error.
      */
    val FAILURE_FILENAME: String = "failure.jp2"
}

/**
  * Imitates [[MockSipiResponderV2]], with hard-coded responses.
  */
class MockSipiResponderV2 extends ActorBasedResponder {
    override def receive: Receive = {
        case getFileMetadataRequestV2: GetImageMetadataRequestV2 => try2Message(sender(), getFileMetadataV2(getFileMetadataRequestV2), log)
        case moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequestV2 => try2Message(sender(), moveTemporaryFileToPermanentStorageV2(moveTemporaryFileToPermanentStorageRequestV2), log)
        case deleteTemporaryFileRequestV2: DeleteTemporaryFileRequestV2 => try2Message(sender(), deleteTemporaryFileV2(deleteTemporaryFileRequestV2), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    private def getFileMetadataV2(getFileMetadataRequestV2: GetImageMetadataRequestV2): Try[GetImageMetadataResponseV2] =
        Success {
            GetImageMetadataResponseV2(
                originalFilename = "test2.tiff",
                originalMimeType = "image/tiff",
                width = 512,
                height = 256
            )
        }

    private def moveTemporaryFileToPermanentStorageV2(moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequestV2): Try[SuccessResponseV2] = {
        if (moveTemporaryFileToPermanentStorageRequestV2.internalFilename == MockSipiResponderV2.FAILURE_FILENAME) {
            Failure(SipiException("Sipi failed to move file to permanent storage"))
        } else {
            Success(SuccessResponseV2("Moved file to permanent storage"))
        }
    }

    private def deleteTemporaryFileV2(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequestV2): Try[SuccessResponseV2] = {
        if (deleteTemporaryFileRequestV2.internalFilename == MockSipiResponderV2.FAILURE_FILENAME) {
            Failure(SipiException("Sipi failed to delete temporary file"))
        } else {
            Success(SuccessResponseV2("Deleted temporary file"))
        }
    }
}
