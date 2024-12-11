/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure
import org.http4s.netty.server.NettyServerBuilder
import org.http4s.server.Router
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.server.interceptor.cors.CORSConfig.AllowedOrigin
import sttp.tapir.server.interceptor.cors.{CORSConfig, CORSInterceptor}
import sttp.tapir.server.metrics.zio.ZioMetrics
import swiss.dasch.Endpoints
import swiss.dasch.config.Configuration.ServiceConfig
import swiss.dasch.version.BuildInfo
import zio.*
import zio.interop.catz.*

object IngestApiServer {

  private val serverOptions = Http4sServerOptions.customiseInterceptors
    .metricsInterceptor(ZioMetrics.default[Task]().metricsInterceptor())
    .corsInterceptor(
      CORSInterceptor.customOrThrow(CORSConfig.default.copy(allowedOrigin = AllowedOrigin.All).exposeAllHeaders),
    )
    .options

  def startup() = for {
    _      <- ZIO.logInfo(s"Starting ${BuildInfo.name}")
    routes <- ZIO.serviceWith[Endpoints](e => ZHttp4sServerInterpreter(serverOptions).from(e.endpoints).toRoutes)
    c      <- ZIO.service[ServiceConfig]
    _      <- ZIO.logInfo(s"Started ${BuildInfo.name}/${BuildInfo.version}, see http://${c.host}:${c.port}/docs")
    server <-
      NettyServerBuilder[Task]
        .bindHttp(c.port, c.host)
        .withHttpApp(Router("/" -> routes).orNotFound)
        .resource
        .useForever
  } yield server
}
