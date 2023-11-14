/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.api

import zio.*
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.macros.accessible
import zio.nio.file.Path

import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.*
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.slice.admin.domain.service.Asset

/**
 * Represents file metadata returned by Sipi.
 *
 * @param originalFilename the file's original filename, if known.
 * @param originalMimeType the file's original MIME type.
 * @param internalMimeType the file's internal MIME type. Always defined (https://dasch.myjetbrains.com/youtrack/issue/DSP-711).
 * @param width            the file's width in pixels, if applicable.
 * @param height           the file's height in pixels, if applicable.
 * @param numpages         the number of pages in the file, if applicable.
 * @param duration         the duration of the file in seconds, if applicable.
 */
case class FileMetadataSipiResponse(
  originalFilename: Option[String],
  originalMimeType: Option[String],
  internalMimeType: String,
  width: Option[Int],
  height: Option[Int],
  numpages: Option[Int],
  duration: Option[BigDecimal],
  fps: Option[BigDecimal]
)
object FileMetadataSipiResponse {
  implicit val codec: JsonCodec[FileMetadataSipiResponse] = DeriveJsonCodec.gen[FileMetadataSipiResponse]
}

@accessible
trait IIIFService {

  /**
   * Asks Sipi for metadata about a file, served from the 'knora.json' route.
   *
   * @param filePath the location of the file.
   * @return a [[FileMetadataSipiResponse]] containing the requested metadata.
   */
  def getFileMetadata(filePath: String): Task[FileMetadataSipiResponse]

  /**
   * Asks Sipi to move a file from temporary storage to permanent storage.
   *
   * @param moveTemporaryFileToPermanentStorageRequestV2 the request.
   * @return a [[SuccessResponseV2]].
   */
  def moveTemporaryFileToPermanentStorage(
    moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest
  ): Task[SuccessResponseV2]

  /**
   * Asks Sipi to delete a temporary file.
   *
   * @param deleteTemporaryFileRequestV2 the request.
   * @return a [[SuccessResponseV2]].
   */
  def deleteTemporaryFile(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest): Task[SuccessResponseV2]

  /**
   * Asks Sipi for a text file used internally by Knora.
   *
   * @param textFileRequest the request message.
   */
  def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse]

  /**
   * Tries to access the IIIF Service.
   */
  def getStatus(): Task[IIIFServiceStatusResponse]

  /**
   * Downloads an asset from Sipi.
   * @param asset The asset to download.
   * @param targetDir The target directory in which the asset should be stored.
   * @param user The user who is downloading the asset.
   * @return The path to the downloaded asset. If the asset could not be downloaded, [[None]] is returned.
   */
  def downloadAsset(asset: Asset, targetDir: Path, user: UserADM): Task[Option[Path]]
}
