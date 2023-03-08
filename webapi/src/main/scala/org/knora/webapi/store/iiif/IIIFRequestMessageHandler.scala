/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif

import zio._

import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.store.sipimessages.DeleteTemporaryFileRequest
import org.knora.webapi.messages.store.sipimessages.GetFileMetadataRequest
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.messages.store.sipimessages.IIIFServiceGetStatus
import org.knora.webapi.messages.store.sipimessages.MoveTemporaryFileToPermanentStorageRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileRequest
import org.knora.webapi.store.iiif.api.IIIFService

trait IIIFRequestMessageHandler extends MessageHandler

final case class IIIFRequestMessageHandlerLive(iiifService: IIIFService) extends IIIFRequestMessageHandler {

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[IIIFRequest]

  override def handle(message: ResponderRequest): Task[Any] = message match {
    case req: GetFileMetadataRequest                     => iiifService.getFileMetadata(req)
    case req: MoveTemporaryFileToPermanentStorageRequest => iiifService.moveTemporaryFileToPermanentStorage(req)
    case req: DeleteTemporaryFileRequest                 => iiifService.deleteTemporaryFile(req)
    case req: SipiGetTextFileRequest                     => iiifService.getTextFileRequest(req)
    case IIIFServiceGetStatus                            => iiifService.getStatus()
    case other                                           => ZIO.logError(s"IIIFServiceManager received an unexpected message: $other")
  }
}

object IIIFRequestMessageHandlerLive {
  val layer: URLayer[IIIFService with MessageRelay, IIIFRequestMessageHandler] = ZLayer.fromZIO {
    for {
      mr      <- ZIO.service[MessageRelay]
      is      <- ZIO.service[IIIFService]
      handler <- mr.subscribe(IIIFRequestMessageHandlerLive(is))
    } yield handler
  }
}
