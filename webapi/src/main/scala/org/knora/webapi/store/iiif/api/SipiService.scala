/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.api

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.RootJsonFormat
import zio.*
import zio.macros.accessible
import zio.nio.file.Path

import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.*
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.slice.admin.domain.service.Asset
import org.knora.webapi.store.iiif.errors.SipiException

/**
 * Represents file metadata returned by Sipi.
 *
 * @param originalFilename the file's original filename, if known.
 * @param originalMimeType the file's original MIME type.
 * @param internalMimeType the file's internal MIME type.
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
) {
  if (originalFilename.contains("")) {
    throw SipiException(s"Sipi returned an empty originalFilename")
  }

  if (originalMimeType.contains("")) {
    throw SipiException(s"Sipi returned an empty originalMimeType")
  }
}

object FileMetadataSipiResponse extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val sipiKnoraJsonResponseFormat: RootJsonFormat[FileMetadataSipiResponse] = jsonFormat8(
    FileMetadataSipiResponse.apply
  )
}

@accessible
trait SipiService {

  /**
   * Asks Sipi for metadata about a file, served from the 'knora.json' route.
   *
   * @param filePath the path to the file.
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
