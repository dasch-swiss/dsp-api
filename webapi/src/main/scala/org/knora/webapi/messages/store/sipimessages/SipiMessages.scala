/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.store.sipimessages

import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.StoreRequest
import org.knora.webapi.messages.traits.RequestWithSender

/**
 * An abstract trait for messages that can be sent to the [[org.knora.webapi.store.iiif.IIIFManager]]
 */
sealed trait IIIFRequest extends StoreRequest

/**
 * An abstract trait for messages that can be sent to [[org.knora.webapi.store.iiif.SipiConnector]].
 */
sealed trait SipiRequest extends IIIFRequest {
  def requestingUser: UserADM
}

/**
 * Requests file metadata from Sipi. A successful response is a [[GetFileMetadataResponse]].
 *
 * @param fileUrl        the URL at which Sipi can serve the file.
 * @param requestingUser the user making the request.
 */
case class GetFileMetadataRequest(fileUrl: String, requestingUser: UserADM) extends SipiRequest

/**
 * Represents file metadata returned by Sipi.
 *
 * @param originalFilename the file's original filename, if known.
 * @param originalMimeType the file's original MIME type.
 * @param internalMimeType the file's internal MIME type. Always defined (https://dasch.myjetbrains.com/youtrack/issue/DSP-711).
 * @param width            the file's width in pixels, if applicable.
 * @param height           the file's height in pixels, if applicable.
 * @param pageCount        the number of pages in the file, if applicable.
 * @param duration         the duration of the file in seconds, if applicable.
 */
case class GetFileMetadataResponse(
  originalFilename: Option[String],
  originalMimeType: Option[String],
  internalMimeType: String,
  width: Option[Int],
  height: Option[Int],
  pageCount: Option[Int],
  duration: Option[BigDecimal],
  fps: Option[BigDecimal]
)

/**
 * Asks Sipi to move a file from temporary to permanent storage.
 *
 * @param internalFilename the name of the file.
 * @param prefix           the prefix under which the file should be stored.
 * @param requestingUser   the user making the request.
 */
case class MoveTemporaryFileToPermanentStorageRequest(internalFilename: String, prefix: String, requestingUser: UserADM)
    extends SipiRequest

/**
 * Asks Sipi to delete a temporary file.
 *
 * @param internalFilename the name of the file.
 * @param requestingUser   the user making the request.
 */
case class DeleteTemporaryFileRequest(internalFilename: String, requestingUser: UserADM) extends SipiRequest

/**
 * Asks Sipi for a text file. Currently only for UTF8 encoded text files.
 *
 * @param fileUrl        the URL pointing to the file.
 * @param requestingUser the user making the request.
 */
case class SipiGetTextFileRequest(fileUrl: String, requestingUser: UserADM, senderName: String)
    extends SipiRequest
    with RequestWithSender

/**
 * Represents a response for [[SipiGetTextFileRequest]].
 *
 * @param content the file content.
 */
case class SipiGetTextFileResponse(content: String)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// IIIF Request ADM

sealed trait IIIFRequestADM extends IIIFRequest

/**
 * Queries IIIF Service status.
 */
case object IIIFServiceGetStatus extends IIIFRequestADM

/**
 * Represents a response for [[IIIFServiceGetStatus]].
 */
sealed trait IIIFServiceStatusResponse

/**
 * Represents a positive response for [[IIIFServiceGetStatus]].
 */
case object IIIFServiceStatusOK extends IIIFServiceStatusResponse

/**
 * Represents a negative response for [[IIIFServiceGetStatus]].
 */
case object IIIFServiceStatusNOK extends IIIFServiceStatusResponse
