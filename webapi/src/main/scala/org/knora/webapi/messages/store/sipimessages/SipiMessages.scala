/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.store.sipimessages

import zio.json.*

import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.store.StoreRequest
import org.knora.webapi.messages.traits.RequestWithSender
import org.knora.webapi.slice.admin.domain.model.User

/**
 * An abstract trait for messages that can be sent to the [[org.knora.webapi.store.iiif.api.SipiService]]
 */
sealed trait IIIFRequest extends StoreRequest with RelayedMessage

sealed trait SipiRequest extends IIIFRequest

/**
 * Asks Sipi to move a file from temporary to permanent storage.
 *
 * @param internalFilename the name of the file.
 * @param prefix           the prefix under which the file should be stored.
 * @param requestingUser   the user making the request.
 */
case class MoveTemporaryFileToPermanentStorageRequest(internalFilename: String, prefix: String, requestingUser: User)
    extends SipiRequest

/**
 * Asks Sipi to delete a temporary file.
 *
 * @param internalFilename the name of the file.
 * @param requestingUser   the user making the request.
 */
case class DeleteTemporaryFileRequest(internalFilename: String, requestingUser: User) extends SipiRequest

/**
 * Asks Sipi for a text file. Currently only for UTF8 encoded text files.
 *
 * @param fileUrl        the URL pointing to the file.
 * @param requestingUser the user making the request.
 */
case class SipiGetTextFileRequest(fileUrl: String, requestingUser: User, senderName: String)
    extends SipiRequest
    with RequestWithSender

/**
 * Represents a response for [[SipiGetTextFileRequest]].
 *
 * @param content the file content.
 */
case class SipiGetTextFileResponse(content: String)

/**
 * Represents the information that Sipi returns about each file that has been uploaded.
 *
 * @param originalFilename the original filename that was submitted to Sipi.
 * @param internalFilename Sipi's internal filename for the stored temporary file.
 * @param temporaryUrl     the URL at which the temporary file can be accessed.
 * @param fileType         `image`, `text`, or `document`.
 */
case class SipiUploadResponseEntry(
  originalFilename: String,
  internalFilename: String,
  temporaryUrl: String,
  fileType: String,
)

/**
 * Represents the information that Sipi returns about each file that has been uploaded to the upload_without_processing route.
 *
 * @param filename            the filename that was submitted to Sipi.
 * @param checksum            the checksum of the file
 * @param algorithm           the algorithm that was used to create the checksum of the file
 */
case class SipiUploadWithoutProcessingResponseEntry(
  filename: String,
  checksum: String,
  algorithm: String,
)

/**
 * Represents Sipi's response to a file upload request.
 *
 * @param uploadedFiles the information about each file that was uploaded.
 */
case class SipiUploadResponse(uploadedFiles: List[SipiUploadResponseEntry])

object SipiUploadResponseJsonProtocol {
  implicit val entry: JsonCodec[SipiUploadResponseEntry] = DeriveJsonCodec.gen[SipiUploadResponseEntry]
  implicit val response: JsonCodec[SipiUploadResponse]   = DeriveJsonCodec.gen[SipiUploadResponse]
}

/**
 * Represents Sipi's response to a file upload without processing request.
 *
 * @param uploadedFiles the information about each file that was uploaded.
 */
case class SipiUploadWithoutProcessingResponse(uploadedFiles: List[SipiUploadWithoutProcessingResponseEntry])

object SipiUploadWithoutProcessingResponseJsonProtocol {
  implicit val withoutResponseEntryCodec: JsonCodec[SipiUploadWithoutProcessingResponseEntry] =
    DeriveJsonCodec.gen[SipiUploadWithoutProcessingResponseEntry]
  implicit val withoutResponseCodec: JsonCodec[SipiUploadWithoutProcessingResponse] =
    DeriveJsonCodec.gen[SipiUploadWithoutProcessingResponse]
}
