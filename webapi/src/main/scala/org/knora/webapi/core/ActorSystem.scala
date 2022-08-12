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

@accessible
trait ActorSystem {
  val system: akka.actor.ActorSystem
  val settings: KnoraSettingsImpl
  val cacheServiceSettings: CacheServiceSettings
}

object ActorSystem {

  private def acquire(config: AppConfig, ec: ExecutionContext) =
    ZIO
      .attempt(
        akka.actor.ActorSystem(
          name = "webapi",
          config = None,
          classLoader = None,
          defaultExecutionContext = Some(ec)
        )
      )
      .tap(_ => ZIO.logInfo(">>> Acquire Actor System <<<"))
      .orDie

  private def release(system: akka.actor.ActorSystem) =
    ZIO
      .fromFuture(_ => system.terminate())
      .tap(_ => ZIO.logInfo(">>> Release Actor System <<<"))
      .orDie

  val layer: ZLayer[AppConfig, Nothing, ActorSystem] =
    ZLayer.scoped {
      for {
        config      <- ZIO.service[AppConfig]
        context     <- ZIO.executor.map(_.asExecutionContext)
        actorSystem <- ZIO.acquireRelease(acquire(config, context))(release _)
      } yield new ActorSystem {
        override val system: akka.actor.ActorSystem             = actorSystem
        override val settings: KnoraSettingsImpl                = KnoraSettings(actorSystem)
        override val cacheServiceSettings: CacheServiceSettings = new CacheServiceSettings(actorSystem.settings.config)
      }
    }.tap(_ => ZIO.logInfo(">>> ActorSystem Initialized <<<"))
}
