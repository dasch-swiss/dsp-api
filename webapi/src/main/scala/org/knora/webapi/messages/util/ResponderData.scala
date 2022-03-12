/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import akka.actor.{ActorRef, ActorSystem}
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.store.cache.settings.CacheSettings

/**
 * Data needed to be passed to each responder.
 *
 * @param system   the actor system.
 * @param appActor the main application actor.
 */
case class ResponderData(
  system: ActorSystem,
  appActor: ActorRef,
  knoraSettings: KnoraSettingsImpl,
  cacheServiceSettings: CacheSettings
)
