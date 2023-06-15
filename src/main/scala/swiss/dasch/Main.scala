/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import swiss.dasch.api.*
import swiss.dasch.api.healthcheck.*
import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.ApiConfig
import swiss.dasch.domain.AssetServiceLive
import zio.*
import zio.config.*
import zio.http.Server
import zio.logging.backend.SLF4J

object Main extends ZIOAppDefault {

  override val bootstrap: ULayer[Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val serverLayer =
    ZLayer
      .service[ApiConfig]
      .flatMap { cfg =>
        Server.defaultWith(_.binding(cfg.get.host, cfg.get.port))
      }
      .orDie

  val routes = HealthCheckRoutes.app ++ ExportEndpoint.app

  private val program = Server.serve(routes)

  override val run =
    program.provide(
      HealthCheckServiceLive.layer,
      Configuration.layer,
      serverLayer,
      AssetServiceLive.layer,
    )
}
