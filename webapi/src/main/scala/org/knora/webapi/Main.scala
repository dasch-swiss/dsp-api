/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio._
import zio.logging.backend.SLF4J

import org.knora.webapi.core.AppServer

object Main extends ZIOApp {

  /**
   * The `Environment` that we require to exist at startup.
   */
  override type Environment = core.LayersLive.DspEnvironmentLive

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  override val bootstrap: ZLayer[
    ZIOAppArgs with Scope,
    Any,
    Environment
  ] = ZLayer.empty ++ Runtime.removeDefaultLoggers ++ SLF4J.slf4j ++ core.LayersLive.dspLayersLive

  // no idea why we need that, but we do
  override val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  /* Here we start our Application */
  override val run =
    (for {
      never <- ZIO.never
    } yield never).provideLayer(AppServer.live)

}
