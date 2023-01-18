/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core
import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http._
import zhttp.http.middleware.Cors
import zhttp.service.Server
import zio.ZLayer
import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.routing.admin.ProjectsRouteZ
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoute

object HttpServerZ {
  // TODO-BL: make this a config
  private val allowedOrigins =
    Set(
      "http://localhost:4200",
      "http://127.0.0.1:4200"
    )
  private def isAllowedOrigin(origin: String): Boolean =
    allowedOrigins.contains(origin)

  private val corsMiddleware =
    Middleware.cors(
      Cors.CorsConfig(
        anyOrigin = false,
        allowedOrigins = isAllowedOrigin,
        allowedHeaders = Some(Set(HttpHeaderNames.AUTHORIZATION.toString))
      )
    )

  private val apiRoutes: URIO[ProjectsRouteZ with ResourceInfoRoute, HttpApp[Any, Nothing]] = for {
    projectsRoute <- ZIO.service[ProjectsRouteZ].map(_.route)
    riRoute       <- ZIO.service[ResourceInfoRoute].map(_.route)
  } yield projectsRoute ++ riRoute

  val layer: ZLayer[ResourceInfoRoute with ProjectsRouteZ with AppConfig, Nothing, Unit] = ZLayer {
    for {
      port   <- ZIO.service[AppConfig].map(_.knoraApi.externalZioPort)
      routes <- apiRoutes
      _      <- Server.start(port, routes @@ corsMiddleware).forkDaemon
      _      <- ZIO.logInfo(">>> Acquire ZIO HTTP Server <<<")
    } yield ()
  }
}
