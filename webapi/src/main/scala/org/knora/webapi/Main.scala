/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio._
import zio.logging.backend.SLF4J

object Main extends App {

  /**
   * The `Environment` that we require to exist at startup.
   * Can be overriden in specs that need other implementations.
   */
  type Environment = core.LayersLive.DspEnvironmentLive

  /**
   * The effect layers from which the App is built.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers = core.LayersLive.dspLayersLive

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  private val bootstrap: ZLayer[
    Any,
    Any,
    Environment
  ] = ZLayer.empty ++ Runtime.removeDefaultLoggers ++ SLF4J.slf4j ++ effectLayers

  // add scope to bootstrap
  private val bootstrapWithScope = Scope.default >>>
    bootstrap +!+ ZLayer.environment[Scope]

  // create a configured runtime
  private val runtime = Unsafe.unsafe { implicit u =>
    Runtime.unsafe
      .fromLayer(bootstrapWithScope)
  }

  /* Here we start our Application */
  private val appServer =
    for {
      _     <- core.AppServer.start(true, true)
      never <- ZIO.never
    } yield never

  /* Here we start our app server */
  Unsafe.unsafe { implicit u =>
    runtime.unsafe.run(appServer)
  }

}
