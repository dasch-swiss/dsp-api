/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.impl

import zio.*
import zio.nio.file.Path

import org.knora.webapi.messages.store.sipimessages.*
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.Asset
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.errors.SipiException

/**
 * Can be used in place of [[SipiServiceLive]] for tests without an actual Sipi server, by returning hard-coded
 * responses simulating responses from Sipi.
 */
case class SipiServiceMock() extends SipiService {

  /**
   * A request with this filename will always cause a Sipi error.
   */
  private val FAILURE_FILENAME: String = "failure.jp2"

  override def getFileMetadataFromSipiTemp(filename: String): Task[FileMetadataSipiResponse] =
    ZIO.succeed(
      FileMetadataSipiResponse(
        originalFilename = Some("test2.tiff"),
        originalMimeType = Some("image/tiff"),
        internalMimeType = "image/jp2",
        width = Some(512),
        height = Some(256),
        numpages = None,
        duration = None,
        fps = None,
      ),
    )

  def moveTemporaryFileToPermanentStorage(
    moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest,
  ): Task[SuccessResponseV2] =
    if (moveTemporaryFileToPermanentStorageRequestV2.internalFilename == FAILURE_FILENAME) {
      ZIO.fail(SipiException("Sipi failed to move file to permanent storage"))
    } else {
      ZIO.succeed(SuccessResponseV2("Moved file to permanent storage"))
    }

  def deleteTemporaryFile(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest): Task[SuccessResponseV2] =
    if (deleteTemporaryFileRequestV2.internalFilename == FAILURE_FILENAME) {
      ZIO.fail(SipiException("Sipi failed to delete temporary file"))
    } else {
      ZIO.succeed(SuccessResponseV2("Deleted temporary file"))
    }

  override def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse] = ???

  override def downloadAsset(asset: Asset, targetDir: Path, user: User): Task[Option[Path]] = ???

  override def getFileMetadataFromDspIngest(
    shortcode: KnoraProject.Shortcode,
    assetId: AssetId,
  ): Task[FileMetadataSipiResponse] = ???
}

object SipiServiceMock {
  val layer: ULayer[SipiServiceMock] = ZLayer.succeed(SipiServiceMock())
}