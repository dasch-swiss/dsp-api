/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio.*

import org.knora.webapi.config.InstrumentationServerConfig
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.core.*
import org.knora.webapi.slice.infrastructure.DspApiServer
import org.knora.webapi.slice.infrastructure.MetricsServer
import org.knora.webapi.util.Logger

object Main extends ZIOApp {

  override def environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  /**
   * The `Environment` that we require to exist at startup.
   */
  override type Environment = LayersLive.DspEnvironmentLive

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  override def bootstrap: ZLayer[Any, Nothing, Environment] =
    Logger.fromEnv() >>> LayersLive.dspLayersLive

  /**
   *  Entrypoint of our Application
   */
  override def run: ZIO[Environment & ZIOAppArgs & Scope, Any, Any] =
    AppServer.make *>
      MetricsServer.make.fork *>
      DspApiServer.make.fork *>
      logStartUp *>
      ZIO.never

  val logStartUp = for {
    apiConfig  <- ZIO.service[KnoraApi]
    instConfig <- ZIO.service[InstrumentationServerConfig]
    _ <- ZIO.logInfo(
           s"Starting api on ${apiConfig.externalKnoraApiBaseUrl}, " +
             s"find docs on ${apiConfig.externalProtocol}://${apiConfig.externalHost}:${instConfig.port}/docs",
         )
  } yield ()
}
