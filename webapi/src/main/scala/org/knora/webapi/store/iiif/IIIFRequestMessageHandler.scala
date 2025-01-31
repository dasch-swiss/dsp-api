/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif

import zio.*

import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileRequest
import org.knora.webapi.store.iiif.api.SipiService

trait IIIFRequestMessageHandler extends MessageHandler

final case class IIIFRequestMessageHandlerLive(iiifService: SipiService) extends IIIFRequestMessageHandler {

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[IIIFRequest]

  override def handle(message: ResponderRequest): Task[Any] = message match {
    case req: SipiGetTextFileRequest => iiifService.getTextFileRequest(req)
    case other                       => ZIO.logError(s"IIIFServiceManager received an unexpected message: $other")
  }
}

object IIIFRequestMessageHandlerLive {
  val layer: URLayer[SipiService & MessageRelay, IIIFRequestMessageHandler] = ZLayer.fromZIO {
    for {
      mr      <- ZIO.service[MessageRelay]
      is      <- ZIO.service[SipiService]
      handler <- mr.subscribe(IIIFRequestMessageHandlerLive(is))
    } yield handler
  }
}
