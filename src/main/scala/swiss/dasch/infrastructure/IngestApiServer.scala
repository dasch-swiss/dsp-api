/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure
import sttp.tapir.server.interceptor.cors.CORSConfig.AllowedOrigin
import sttp.tapir.server.interceptor.cors.{ CORSConfig, CORSInterceptor }
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.ziohttp
import sttp.tapir.server.ziohttp.{ ZioHttpInterpreter, ZioHttpServerOptions }
import swiss.dasch.Endpoints
import swiss.dasch.config.Configuration.ServiceConfig
import swiss.dasch.version.BuildInfo
import zio.*
import zio.http.*

object IngestApiServer {

  private val serverOptions = ZioHttpServerOptions
    .customiseInterceptors
    .metricsInterceptor(ZioMetrics.default[Task]().metricsInterceptor())
    .corsInterceptor(
      CORSInterceptor.customOrThrow(CORSConfig.default.copy(allowedOrigin = AllowedOrigin.All).exposeAllHeaders)
    )
    .options

  def startup(): ZIO[ServiceConfig with Server with Endpoints, Nothing, Unit] = for {
    _   <- ZIO.logInfo(s"Starting ${BuildInfo.name}")
    app <- ZIO.serviceWith[Endpoints](_.endpoints).map(ZioHttpInterpreter(serverOptions).toHttp(_))
    _   <- Server.install(app.withDefaultErrorResponse)
    _   <- ZIO.serviceWithZIO[ServiceConfig](c =>
             ZIO.logInfo(s"Started ${BuildInfo.name}/${BuildInfo.version}, see http://${c.host}:${c.port}/docs")
           )
  } yield ()

  val layer: URLayer[ServiceConfig, Server] = ZLayer
    .service[ServiceConfig]
    .flatMap { cfg =>
      Server.defaultWith(_.binding(cfg.get.host, cfg.get.port).enableRequestStreaming)
    }
    .orDie
}
