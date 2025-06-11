/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.*
import org.knora.webapi.slice.infrastructure.DspApiServer
import org.knora.webapi.slice.infrastructure.MetricsServer
import org.knora.webapi.util.Logger

object Main extends ZIOApp {

  override def environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  /**
   * The `Environment` that we require to exist at startup.
   */
  override type Environment = AppConfig.AppConfigurations & LayersLive.Environment

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  override def bootstrap: ZLayer[Any, Nothing, Environment] =
    Logger.fromEnv() >>> AppConfig.layer >+> LayersLive.layer

  override val run = for {
    _ <- Db.init
    _ <- DspApiServer.make
    _ <- MetricsServer.make
    _ <- ZIO.never
  } yield ()
}
