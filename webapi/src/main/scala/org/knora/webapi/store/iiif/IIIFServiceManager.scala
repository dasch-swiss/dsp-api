/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.iiif

import org.knora.webapi.messages.store.sipimessages.DeleteTemporaryFileRequest
import org.knora.webapi.messages.store.sipimessages.GetFileMetadataRequest
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.messages.store.sipimessages.IIIFServiceGetStatus
import org.knora.webapi.messages.store.sipimessages.MoveTemporaryFileToPermanentStorageRequest
import org.knora.webapi.messages.store.sipimessages.SipiGetTextFileRequest
import org.knora.webapi.store.iiif.api.IIIFService
import zio._
import zio.macros.accessible

/**
 * Makes requests to IIIF servers.
 */
@accessible
trait IIIFServiceManager {

  /**
   * Main entry point for the Actor based architecture. Here we only translate the
   * incoming Akka messages to calls to ZIO based implementations. Each ZIO response
   * is then translated back to Akka through [[ActorUtil.zio2Message]].
   */
  def receive(message: IIIFRequest): ZIO[Any, Nothing, Any]

}

object IIIFServiceManager {
  val layer: ZLayer[IIIFService, Nothing, IIIFServiceManager] =
    ZLayer {
      for {
        iiifs <- ZIO.service[IIIFService]
      } yield new IIIFServiceManager {

        override def receive(message: IIIFRequest) = message match {
          case req: GetFileMetadataRequest                     => iiifs.getFileMetadata(req)
          case req: MoveTemporaryFileToPermanentStorageRequest => iiifs.moveTemporaryFileToPermanentStorage(req)
          case req: DeleteTemporaryFileRequest                 => iiifs.deleteTemporaryFile(req)
          case req: SipiGetTextFileRequest                     => iiifs.getTextFileRequest(req)
          case IIIFServiceGetStatus                            => iiifs.getStatus()
          case other                                           => ZIO.logError(s"IIIFServiceManager received an unexpected message: $other")
        }
      }
    }
}
