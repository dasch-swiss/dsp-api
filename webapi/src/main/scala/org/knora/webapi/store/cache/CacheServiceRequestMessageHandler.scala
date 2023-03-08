/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache

import zio._
import zio.metrics.Metric

import java.time.temporal.ChronoUnit

import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.store.cacheservicemessages._
import org.knora.webapi.store.cache.api.CacheService

trait CacheServiceRequestMessageHandler extends MessageHandler

final case class CacheServiceRequestMessageHandlerLive(cacheService: CacheService)
    extends CacheServiceRequestMessageHandler {

  private val cacheServiceWriteUserTimer = Metric
    .timer(
      name = "cache-service-write-user",
      chronoUnit = ChronoUnit.NANOS
    )

  private val cacheServiceWriteProjectTimer = Metric
    .timer(
      name = "cache-service-write-project",
      chronoUnit = ChronoUnit.NANOS
    )

  private val cacheServiceReadProjectTimer = Metric
    .timer(
      name = "cache-service-read-project",
      chronoUnit = ChronoUnit.NANOS
    )
  override def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[CacheServiceRequest]

  override def handle(message: ResponderRequest): Task[Any] = message match {
    case CacheServicePutUserADM(value)      => cacheService.putUserADM(value) @@ cacheServiceWriteUserTimer.trackDuration
    case CacheServiceGetUserADM(identifier) => cacheService.getUserADM(identifier)
    case CacheServicePutProjectADM(value) =>
      cacheService.putProjectADM(value) @@ cacheServiceWriteProjectTimer.trackDuration
    case CacheServiceGetProjectADM(identifier) =>
      cacheService.getProjectADM(identifier) @@ cacheServiceReadProjectTimer.trackDuration
    case CacheServicePutString(key, value)   => cacheService.putStringValue(key, value)
    case CacheServiceGetString(key)          => cacheService.getStringValue(key)
    case CacheServiceRemoveValues(keys)      => cacheService.removeValues(keys)
    case CacheServiceFlushDB(requestingUser) => cacheService.flushDB(requestingUser)
    case CacheServiceGetStatus               => cacheService.getStatus
    case other                               => ZIO.logError(s"CacheServiceManager received an unexpected message: $other")
  }
}

object CacheServiceRequestMessageHandlerLive {
  val layer: URLayer[CacheService with MessageRelay, CacheServiceRequestMessageHandler] = ZLayer.fromZIO {
    for {
      mr      <- ZIO.service[MessageRelay]
      cs      <- ZIO.service[CacheService]
      handler <- mr.subscribe(CacheServiceRequestMessageHandlerLive(cs))
    } yield handler
  }
}
