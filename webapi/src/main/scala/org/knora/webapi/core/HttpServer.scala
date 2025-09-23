/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.*
import zio.http.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.routing.CompleteApiServerEndpoints
import sttp.tapir.server.interceptor.cors.CORSConfig.AllowedOrigin
import sttp.tapir.server.interceptor.cors.{CORSConfig, CORSInterceptor}
import sttp.tapir.server.metrics.zio.ZioMetrics
import sttp.tapir.server.ziohttp.{ZioHttpServerOptions, ZioHttpInterpreter}
import sttp.tapir.ztapir.*

object HttpServer {

  private def options = ZioHttpServerOptions.default

  val layer = ZLayer.scoped(createServer)

  private def createServer = for {
    port      <- ZIO.serviceWith[KnoraApi](_.internalPort)
    endpoints <- ZIO.serviceWith[CompleteApiServerEndpoints](_.serverEndpoints)
    httpApp    = ZioHttpInterpreter(options).toHttp(endpoints)
    _         <- Server.install(httpApp).provide(Server.defaultWithPort(port))
    _         <- Console.printLine(s"Go to http://localhost:$config.knoraApi.externalPort/docs to open SwaggerUI")
  } yield ()
}
