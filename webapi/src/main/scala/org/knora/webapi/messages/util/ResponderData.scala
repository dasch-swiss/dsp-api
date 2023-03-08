/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.util.Timeout

import scala.concurrent.ExecutionContext

import org.knora.webapi.config.AppConfig
import org.knora.webapi.responders.ActorDeps
import org.knora.webapi.store.cache.settings.CacheServiceSettings

/**
 * Data needed to be passed to each responder.
 *
 * @param actorDeps all dependencies necessary for interacting with the [[org.knora.webapi.core.actors.RoutingActor]]
 * @param appConfig the application configuration for creating the [[CacheServiceSettings]]
 */
case class ResponderData(actorDeps: ActorDeps, appConfig: AppConfig) {
  val cacheServiceSettings: CacheServiceSettings = new CacheServiceSettings(appConfig)

  val appActor: ActorRef                 = actorDeps.appActor
  val executionContext: ExecutionContext = actorDeps.executionContext
  val system: ActorSystem                = actorDeps.system
  val timeout: Timeout                   = actorDeps.timeout
}
