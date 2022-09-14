/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.store.cache.settings.CacheServiceSettings

object ActorSystemTest {

  def layer(sys: akka.actor.ActorSystem): ZLayer[AppConfig, Nothing, ActorSystem] =
    ZLayer.scoped {
      for {
        config  <- ZIO.service[AppConfig]
        context <- ZIO.executor.map(_.asExecutionContext)
      } yield new ActorSystem {
        override val system: akka.actor.ActorSystem             = sys
        override val appConfig                                  = config
        override val cacheServiceSettings: CacheServiceSettings = new CacheServiceSettings(appConfig)
      }
    }
}
