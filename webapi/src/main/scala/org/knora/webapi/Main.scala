/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio.*

import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.core.*
import org.knora.webapi.core.LayersLive.Environment
import org.knora.webapi.slice.infrastructure.MetricsServer

object Main extends ZIOApp {

  override def environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  /**
   * The `Environment` that we require to exist at startup.
   */
  override type Environment = AppConfigurations & LayersLive.Environment

  /**
   * The layers provided to the application.
   */
  override def bootstrap: ULayer[Environment] = LayersLive.bootstrap

  /**
   *  Entrypoint of our Application
   */
  override def run: ZIO[Environment & ZIOAppArgs & Scope, Any, Any] =
    (Db.init *> DspApiServer.startup *> MetricsServer.make).provideSomeAuto(DspApiServer.layer)

}
