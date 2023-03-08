/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio._

import org.knora.webapi.core._
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
  override def bootstrap: ZLayer[Any, Nothing, LayersLive.DspEnvironmentLive] =
    Logger.fromEnv() >>> LayersLive.dspLayersLive

  /**
   *  Entrypoint of our Application
   */
  override def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] =
    InstrumentationServer.make *> AppServer.make *> ZIO.never
}
