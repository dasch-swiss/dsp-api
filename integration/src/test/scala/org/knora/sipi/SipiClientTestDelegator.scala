/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sipi

import zio.Task
import zio.ULayer
import zio.ZLayer
import zio.nio.file.Path

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.store.sipimessages.DeleteTemporaryFileRequest
import org.knora.webapi.messages.store.sipimessages.MoveTemporaryFileToPermanentStorageRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileResponse
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.Asset
import org.knora.webapi.slice.admin.domain.service.DspIngestClient
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.iiif.impl.SipiServiceLive
import org.knora.webapi.store.iiif.impl.SipiServiceMock

case class WhichSipiService(useLive: Boolean)

object WhichSipiService {
  val live: ULayer[WhichSipiService] = ZLayer.succeed(WhichSipiService(useLive = true))
  val mock: ULayer[WhichSipiService] = ZLayer.succeed(WhichSipiService(useLive = false))
}

/**
 * A delegator for the Sipi service, used in tests.
 * Depending on the [[WhichSipiService]] configuration, it delegates to either the [[SipiServiceLive]] or the [[SipiServiceMock]].
 */
case class SipiServiceTestDelegator(
  private val whichSipi: WhichSipiService,
  private val live: SipiServiceLive,
  private val mock: SipiServiceMock,
) extends SipiService {

  private final val sipiService =
    if (whichSipi.useLive) { live }
    else { mock }

  /**
   * Asks Sipi for metadata about a file in the tmp folder, served from the 'knora.json' route.
   *
   * @param filename the path to the file.
   * @return a [[FileMetadataSipiResponse]] containing the requested metadata.
   */
  override def getFileMetadataFromSipiTemp(filename: String): Task[FileMetadataSipiResponse] =
    sipiService.getFileMetadataFromSipiTemp(filename)

  /**
   * Asks DSP-Ingest for metadata about a file in permanent location, served from the 'knora.json' route.
   *
   * @param shortcode the shortcode of the project.
   * @param assetId   for the file.
   * @return a [[FileMetadataSipiResponse]] containing the requested metadata.
   */
  override def getFileMetadataFromDspIngest(
    shortcode: KnoraProject.Shortcode,
    assetId: AssetId,
  ): Task[FileMetadataSipiResponse] =
    sipiService.getFileMetadataFromDspIngest(shortcode, assetId)

  /**
   * Asks Sipi to move a file from temporary storage to permanent storage.
   *
   * @param moveTemporaryFileToPermanentStorageRequestV2 the request.
   * @return a [[SuccessResponseV2]].
   */
  override def moveTemporaryFileToPermanentStorage(
    moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest,
  ): Task[SuccessResponseV2] =
    sipiService.moveTemporaryFileToPermanentStorage(moveTemporaryFileToPermanentStorageRequestV2)

  /**
   * Asks Sipi to delete a temporary file.
   *
   * @param deleteTemporaryFileRequestV2 the request.
   * @return a [[SuccessResponseV2]].
   */
  override def deleteTemporaryFile(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest): Task[SuccessResponseV2] =
    sipiService.deleteTemporaryFile(deleteTemporaryFileRequestV2)

  /**
   * Asks Sipi for a text file used internally by Knora.
   *
   * @param textFileRequest the request message.
   */
  override def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse] =
    sipiService.getTextFileRequest(textFileRequest)

  /**
   * Downloads an asset from Sipi.
   *
   * @param asset     The asset to download.
   * @param targetDir The target directory in which the asset should be stored.
   * @param user      The user who is downloading the asset.
   * @return The path to the downloaded asset. If the asset could not be downloaded, [[None]] is returned.
   */
  override def downloadAsset(asset: Asset, targetDir: Path, user: User): Task[Option[Path]] =
    sipiService.downloadAsset(asset, targetDir, user)
}

object SipiServiceTestDelegator {
  val layer: ZLayer[AppConfig & DspIngestClient & JwtService & WhichSipiService, Nothing, SipiService] =
    SipiServiceMock.layer >+> SipiServiceLive.layer >>> ZLayer.derive[SipiServiceTestDelegator]
}
