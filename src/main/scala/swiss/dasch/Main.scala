/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import swiss.dasch.api.*
import swiss.dasch.api.healthcheck.*
import swiss.dasch.api.info.InfoEndpoint
import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.{ DspIngestApiConfig, JwtConfig, StorageConfig }
import swiss.dasch.domain.{ AssetService, AssetServiceLive }
import zio.*
import zio.config.*
import zio.http.{ Http, HttpApp, Request, Response, Server }
import zio.logging.backend.SLF4J

object Main extends ZIOAppDefault {

  override val bootstrap: ULayer[Unit] = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  private val serverLayer =
    ZLayer
      .service[DspIngestApiConfig]
      .flatMap { cfg =>
        Server.defaultWith(_.binding(cfg.get.host, cfg.get.port))
      }
      .orDie

  private val serviceRoutes    = (ExportEndpoint.app ++ ImportEndpoint.app) @@ Authenticator.middleware
  private val managementRoutes = HealthCheckRoutes.app ++ InfoEndpoint.app
  private val routes           = managementRoutes ++ serviceRoutes
  private val program          = Server.serve(routes)

  override val run: ZIO[Any with Scope, ReadError[String], Nothing] =
    program.provide(
      HealthCheckServiceLive.layer,
      Configuration.layer,
      serverLayer,
      AssetServiceLive.layer,
      AuthenticatorLive.layer,
    )
}
