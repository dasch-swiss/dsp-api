/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio._

object Main extends ZIOApp {

  /**
   * The `Environment` that we require to exist at startup.
   */
  type Environment = core.LayersLive.DSPEnvironmentLive

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  override val bootstrap: ZLayer[
    ZIOAppArgs with Scope,
    Any,
    Environment
  ] =
    ZLayer.empty ++ Runtime.removeDefaultLoggers ++ logging.consoleJson() ++ core.LayersLive.dspLayersLive

  // no idea why we need that, but we do
  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  /* Here we start our Application */
  val run =
    for {
      _     <- core.AppServer(true, true)
      never <- ZIO.never
    } yield never

}
