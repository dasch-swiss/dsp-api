/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.knora.webapi.settings.KnoraSettingsImpl
import zio._
import zio.macros.accessible
import org.knora.webapi.store.cache.settings.CacheServiceSettings
import org.knora.webapi.config.AppConfig
import scala.concurrent.ExecutionContext
import org.knora.webapi.settings.KnoraSettings

object ActorSystemTestImpl {

  def layer(sys: akka.actor.ActorSystem): ZLayer[Any, Nothing, ActorSystem] =
    ZLayer
      .succeed(
        new ActorSystem {
          override val system: akka.actor.ActorSystem             = sys
          override val settings: KnoraSettingsImpl                = KnoraSettings(system)
          override val cacheServiceSettings: CacheServiceSettings = new CacheServiceSettings(system.settings.config)
        }
      )
      .tap(_ => ZIO.logInfo(">>> ActorSystem Initialized <<<"))
}
