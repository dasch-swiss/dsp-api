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
import sttp.tapir.server.interceptor.cors.CORSConfig
import sttp.tapir.server.interceptor.cors.CORSInterceptor
import sttp.tapir.server.metrics.zio.ZioMetrics

object DspApiServer {

  private def options: ZioHttpServerOptions[Any] =
    ZioHttpServerOptions.customiseInterceptors
      .metricsInterceptor(ZioMetrics.default[Task]().metricsInterceptor())
      .corsInterceptor(
        CORSInterceptor.customOrThrow(CORSConfig.default.allowAllMethods.allowAllOrigins.exposeAllHeaders),
      )
      .options

  def startup(): ZIO[Endpoints & KnoraApi & Server, Throwable, Unit] =
    for {
      c   <- ZIO.service[KnoraApi]
      app <- ZIO.serviceWith[Endpoints](_.serverEndpoints).map(ZioHttpInterpreter(options).toHttp(_))
      _   <- ZIO.serviceWithZIO[Server](_.install(app)): @annotation.nowarn
      _   <- ZIO.logInfo(s"http://localhost:${c.internalPort}/version ")
    } yield ()
}
