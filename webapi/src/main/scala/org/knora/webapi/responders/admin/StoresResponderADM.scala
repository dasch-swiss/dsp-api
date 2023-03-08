/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio._

import dsp.errors.ForbiddenException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.admin.responder.storesmessages.ResetTriplestoreContentRequestADM
import org.knora.webapi.messages.admin.responder.storesmessages.ResetTriplestoreContentResponseADM
import org.knora.webapi.messages.admin.responder.storesmessages.StoreResponderRequestADM
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.responders.Responder
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.triplestore.api.TriplestoreService

/**
 * This responder is used by [[org.knora.webapi.routing.admin.StoreRouteADM]], for piping through HTTP requests to the
 * 'Store Module'
 */
trait StoresResponderADM {

  /**
   * Resets the triplestore with provided data, adding defaults optionally.
   *
   * @param rdfDataObjects the payload consisting of a list of [[RdfDataObject]] send inside the message.
   * @return a [[ResetTriplestoreContentResponseADM]].
   */
  def resetTriplestoreContent(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean = true
  ): Task[ResetTriplestoreContentResponseADM]
}

final case class StoresResponderADMLive(
  appConfig: AppConfig,
  cacheService: CacheService,
  messageRelay: MessageRelay,
  triplestoreService: TriplestoreService
) extends StoresResponderADM
    with MessageHandler {

  /**
   * A user representing the Knora API server, used in those cases where a user is required.
   */
  private val systemUser = KnoraSystemInstances.Users.SystemUser

  override def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[StoreResponderRequestADM]

  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case r: ResetTriplestoreContentRequestADM => resetTriplestoreContent(r.rdfDataObjects.toList, r.prependDefaults)
    case other                                => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  override def resetTriplestoreContent(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean = true
  ): Task[ResetTriplestoreContentResponseADM] =
    for {
      _ <- ZIO.logDebug(s"resetTriplestoreContent - called")
      _ <- ZIO
             .fail(
               ForbiddenException(
                 "The ResetTriplestoreContent operation is not allowed. Did you start the server with the right flag?"
               )
             )
             .when(!appConfig.allowReloadOverHttp)
      resetResponse          <- triplestoreService.resetTripleStoreContent(rdfDataObjects, prependDefaults)
      _                      <- ZIO.logDebug(s"resetTriplestoreContent - triplestore reset done - ${resetResponse.toString}")
      loadOntologiesResponse <- messageRelay.ask[SuccessResponseV2](LoadOntologiesRequestV2(systemUser))
      _                      <- ZIO.logDebug(s"resetTriplestoreContent - load ontology done - ${loadOntologiesResponse.toString}")
      _                      <- cacheService.flushDB(systemUser)
      _                      <- ZIO.logDebug(s"resetTriplestoreContent - flushing cache store done.")
    } yield ResetTriplestoreContentResponseADM("success")
}

object StoresResponderADMLive {
  val layer: URLayer[TriplestoreService with MessageRelay with CacheService with AppConfig, StoresResponderADMLive] =
    ZLayer.fromZIO {
      for {
        config  <- ZIO.service[AppConfig]
        cs      <- ZIO.service[CacheService]
        mr      <- ZIO.service[MessageRelay]
        ts      <- ZIO.service[TriplestoreService]
        handler <- mr.subscribe(StoresResponderADMLive(config, cs, mr, ts))
      } yield handler
    }
}
