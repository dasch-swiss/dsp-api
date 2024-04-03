/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.impl

import zio._
import zio.nio.file.Path

import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.slice.admin.api.model.MaintenanceRequests.AssetId
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.Asset
import org.knora.webapi.store.iiif.api.FileMetadataSipiResponse
import org.knora.webapi.store.iiif.api.SipiService

/**
 * Can be used in place of [[SipiServiceLive]] for tests without an actual Sipi server, by returning hard-coded
 * responses simulating responses from Sipi.
 */
case class SipiServiceMock() extends SipiService {

  override def getFileMetadataFromSipiTemp(filename: String): Task[FileMetadataSipiResponse] = ???

  override def moveTemporaryFileToPermanentStorage(
    moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest,
  ): Task[SuccessResponseV2] = ???

  override def deleteTemporaryFile(
    deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest,
  ): Task[SuccessResponseV2] = ???

  override def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse] = ???

  override def getStatus(): Task[IIIFServiceStatusResponse] = ZIO.succeed(IIIFServiceStatusOK)

  override def downloadAsset(asset: Asset, targetDir: Path, user: User): Task[Option[Path]] = ???

  override def getFileMetadataFromDspIngest(
    shortcode: KnoraProject.Shortcode,
    assetId: AssetId,
  ): Task[FileMetadataSipiResponse] = ???
}

object SipiServiceMock {

  val layer: ZLayer[Any, Nothing, SipiService] =
    ZLayer
      .succeed(SipiServiceMock())
      .tap(_ => ZIO.logInfo(">>> Mock Sipi IIIF Service Initialized <<<"))

}
