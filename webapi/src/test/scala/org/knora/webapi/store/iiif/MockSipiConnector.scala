/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.http.scaladsl.util.FastFuture
import org.knora.webapi.exceptions.SipiException
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.util.ActorUtil._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
 * Constants for [[MockSipiConnector]].
 */
object MockSipiConnector {

  /**
   * A request to [[MockSipiConnector]] with this filename will always cause the responder to simulate a Sipi
   * error.
   */
  val FAILURE_FILENAME: String = "failure.jp2"
}

/**
 * Takes the place of [[SipiConnector]] for tests without an actual Sipi server, by returning hard-coded responses
 * simulating responses from Sipi.
 */
class MockSipiConnector extends Actor with ActorLogging {

  implicit val system: ActorSystem = context.system
  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  def receive = {
    case getFileMetadataRequest: GetFileMetadataRequest =>
      try2Message(sender(), getFileMetadata(getFileMetadataRequest), log)
    case moveTemporaryFileToPermanentStorageRequest: MoveTemporaryFileToPermanentStorageRequest =>
      try2Message(sender(), moveTemporaryFileToPermanentStorage(moveTemporaryFileToPermanentStorageRequest), log)
    case deleteTemporaryFileRequest: DeleteTemporaryFileRequest =>
      try2Message(sender(), deleteTemporaryFile(deleteTemporaryFileRequest), log)
    case IIIFServiceGetStatus => future2Message(sender(), FastFuture.successful(IIIFServiceStatusOK), log)
    case other                => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
  }

  private def getFileMetadata(getFileMetadataRequestV2: GetFileMetadataRequest): Try[GetFileMetadataResponse] =
    Success {
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
    }

  private def moveTemporaryFileToPermanentStorage(
    moveTemporaryFileToPermanentStorageRequestV2: MoveTemporaryFileToPermanentStorageRequest
  ): Try[SuccessResponseV2] =
    if (moveTemporaryFileToPermanentStorageRequestV2.internalFilename == MockSipiConnector.FAILURE_FILENAME) {
      Failure(SipiException("Sipi failed to move file to permanent storage"))
    } else {
      Success(SuccessResponseV2("Moved file to permanent storage"))
    }

  private def deleteTemporaryFile(deleteTemporaryFileRequestV2: DeleteTemporaryFileRequest): Try[SuccessResponseV2] =
    if (deleteTemporaryFileRequestV2.internalFilename == MockSipiConnector.FAILURE_FILENAME) {
      Failure(SipiException("Sipi failed to delete temporary file"))
    } else {
      Success(SuccessResponseV2("Deleted temporary file"))
    }
}
