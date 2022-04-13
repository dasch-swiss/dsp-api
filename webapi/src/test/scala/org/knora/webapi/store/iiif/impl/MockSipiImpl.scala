/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif.impl

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.http.scaladsl.util.FastFuture
import org.knora.webapi.exceptions.SipiException
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.util.ActorUtil._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import org.knora.webapi.store.iiif.api.IIIFService
import zio._

/**
 * Can be used in place of [[IIIFServiceSipiImpl]] for tests without an actual Sipi server, by returning hard-coded
 * responses simulating responses from Sipi.
 */
case class MockSipiImpl() extends IIIFService {

  /**
   * A request to [[MockSipiConnector]] with this filename will always cause the responder to simulate a Sipi
   * error.
   */
  private val FAILURE_FILENAME: String = "failure.jp2"

  override def getFileMetadata(getFileMetadataRequestV2: GetFileMetadataRequest): Task[GetFileMetadataResponse] =
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

  override def moveTemporaryFileToPermanentStorage(
    moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest
  ): Task[SuccessResponseV2] =
    if (moveTemporaryFileToPermanentStorageRequestV2.internalFilename == FAILURE_FILENAME) {
      ZIO.fail(SipiException("Sipi failed to move file to permanent storage"))
    } else {
      ZIO.succeed(SuccessResponseV2("Moved file to permanent storage"))
    }

  override def deleteTemporaryFile(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest): Task[SuccessResponseV2] =
    if (deleteTemporaryFileRequestV2.internalFilename == FAILURE_FILENAME) {
      ZIO.fail(SipiException("Sipi failed to delete temporary file"))
    } else {
      ZIO.succeed(SuccessResponseV2("Deleted temporary file"))
    }

  override def getTextFileRequest(textFileRequest: SipiGetTextFileRequest): Task[SipiGetTextFileResponse] = ???

  override def getStatus(): Task[IIIFServiceStatusResponse] = ZIO.succeed(IIIFServiceStatusOK)
}

object MockSipiImpl {

  val layer: ZLayer[Any, Nothing, IIIFService] = {
    ZLayer.succeed(MockSipiImpl()).tap(_ => ZIO.debug(">>> Mock Sipi IIIF Service Initialized <<<"))
  }

}
