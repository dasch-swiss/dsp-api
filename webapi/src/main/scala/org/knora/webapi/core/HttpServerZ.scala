/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio._
import zio.http._
import zio.http.middleware.Cors

import org.knora.webapi.config.AppConfig
import org.knora.webapi.routing.admin.ProjectsRouteZ
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoute
import zio.http.model.Method

object HttpServerZ {

  private val corsMiddleware =
    for {
      config <- ZIO.service[AppConfig]
      corsConfig =
        Cors.CorsConfig(
          anyOrigin = false,
          anyMethod = false,
          allowedMethods = Some(Set(Method.GET, Method.PUT, Method.DELETE, Method.POST)),
          allowedOrigins = { origin =>
            config.httpServer.corsAllowedOrigins.contains(origin)
          }
        )
    } yield Middleware.cors(corsConfig)

  private val apiRoutes: URIO[ProjectsRouteZ with ResourceInfoRoute, HttpApp[Any, Nothing]] = for {
    projectsRoute <- ZIO.service[ProjectsRouteZ].map(_.route)
    riRoute       <- ZIO.service[ResourceInfoRoute].map(_.route)
  } yield projectsRoute ++ riRoute

  val layer: ZLayer[ResourceInfoRoute with ProjectsRouteZ with AppConfig, Nothing, Unit] = ZLayer {
    for {
      port        <- ZIO.service[AppConfig].map(_.knoraApi.externalZioPort)
      routes      <- apiRoutes
      cors        <- corsMiddleware
      serverConfig = ZLayer.succeed(ServerConfig.default.port(port))
      _           <- Server.serve(routes @@ cors).provide(Server.live, serverConfig).forkDaemon
      _           <- ZIO.logInfo(">>> Acquire ZIO HTTP Server <<<")
    } yield ()
  }
}
