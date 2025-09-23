/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import zio.*
import zio.http.*

import org.knora.webapi.config.KnoraApi
import org.knora.webapi.routing.Endpoints

object HttpServer {

  private def options: ZioHttpServerOptions[Any] = ZioHttpServerOptions.default

  val layer = ZLayer.scoped(apiServer).orDie

  private def apiServer: ZIO[Endpoints & KnoraApi, Throwable, Unit] = for {
    apiConfig <- ZIO.service[KnoraApi]
    endpoints <- ZIO.serviceWith[Endpoints](_.serverEndpoints)
    routes     = ZioHttpInterpreter(options).toHttp(endpoints)
    _         <- Server.install(routes).provide(Server.defaultWithPort(apiConfig.internalPort)): @annotation.nowarn
    _         <- Console.printLine(s"Go to http://localhost:${apiConfig.externalPort}/docs to open SwaggerUI")
    _         <- ZIO.never.unit
  } yield ()
}
