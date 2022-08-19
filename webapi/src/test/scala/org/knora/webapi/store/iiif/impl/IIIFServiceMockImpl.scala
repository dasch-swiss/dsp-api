/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.impl

import zio._

import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.iiif.errors.SipiException

/**
 * Can be used in place of [[IIIFServiceSipiImpl]] for tests without an actual Sipi server, by returning hard-coded
 * responses simulating responses from Sipi.
 */
case class IIIFServiceMockImpl() extends IIIFService {

  /**
   * A request with this filename will always cause a Sipi error.
   */
  private val FAILURE_FILENAME: String = "failure.jp2"

  def getFileMetadata(getFileMetadataRequestV2: GetFileMetadataRequest): UIO[GetFileMetadataResponse] =
    ZIO.succeed(
      GetFileMetadataResponse(
        originalFilename = Some("test2.tiff"),
        originalMimeType = Some("image/tiff"),
        internalMimeType = "image/jp2",
        width = Some(512),
        height = Some(256),
        pageCount = None,
        duration = None,
        fps = None
      )
    )

  def moveTemporaryFileToPermanentStorage(
    moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest
  ): UIO[SuccessResponseV2] =
    if (moveTemporaryFileToPermanentStorageRequestV2.internalFilename == FAILURE_FILENAME) {
      ZIO.die(SipiException("Sipi failed to move file to permanent storage"))
    } else {
      ZIO.succeed(SuccessResponseV2("Moved file to permanent storage"))
    }

  def deleteTemporaryFile(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest): UIO[SuccessResponseV2] =
    if (deleteTemporaryFileRequestV2.internalFilename == FAILURE_FILENAME) {
      ZIO.die(SipiException("Sipi failed to delete temporary file"))
    } else {
      ZIO.succeed(SuccessResponseV2("Deleted temporary file"))
    }

  override def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): UIO[SipiGetTextFileResponse] = ???

  override def getStatus(): UIO[IIIFServiceStatusResponse] = ZIO.succeed(IIIFServiceStatusOK)
}

object IIIFServiceMockImpl {

  val layer: ZLayer[Any, Nothing, IIIFService] =
    ZLayer
      .succeed(IIIFServiceMockImpl())
      .tap(_ => ZIO.debug(">>> Mock Sipi IIIF Service Initialized <<<"))

}
