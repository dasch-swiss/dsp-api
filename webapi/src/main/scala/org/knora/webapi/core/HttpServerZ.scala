/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core
import zhttp.http._
import zhttp.http.middleware.Cors
import zhttp.service.Server
import zio.ZLayer
import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.routing.admin.ProjectsRouteZ
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoute

object HttpServerZ {

  private val corsMiddleware =
    for {
      config <- ZIO.service[AppConfig]
      corsConfig =
        Cors.CorsConfig(
          anyOrigin = false,
          allowedOrigins = { x =>
            val y = config.zioHttp.corsAllowedOrigins.contains(x)
            println(s"CORS Checked: $x (result: $y) ")
            y
          }
          // allowedHeaders = Some(Set(config.zioHttp.corsAllowedHeaders)),
          // allowedHeaders = Some(Set("*"))
          // allowedHeaders = Some(Set(HttpHeaderNames.CONTENT_TYPE.toString, HttpHeaderNames.AUTHORIZATION.toString))
          // allowedHeaders = Some(Set("authorization,content-type"))
          // allowedHeaders = Some(Set("Content-Type"))
          // allowedHeaders = Some(Set("authorization", "content-type"))
          // allowedHeaders = Some(Set("authorization", "Content-Type"))
          // allowedHeaders = None
        )
    } yield Middleware.cors(corsConfig)

  private val apiRoutes: URIO[ProjectsRouteZ with ResourceInfoRoute, HttpApp[Any, Nothing]] = for {
    projectsRoute <- ZIO.service[ProjectsRouteZ].map(_.route)
    riRoute       <- ZIO.service[ResourceInfoRoute].map(_.route)
  } yield projectsRoute ++ riRoute

  val layer: ZLayer[ResourceInfoRoute with ProjectsRouteZ with AppConfig, Nothing, Unit] = ZLayer {
    for {
      port   <- ZIO.service[AppConfig].map(_.knoraApi.externalZioPort)
      routes <- apiRoutes
      cors   <- corsMiddleware
      _      <- Server.start(port, routes @@ cors).forkDaemon
      _      <- ZIO.logInfo(">>> Acquire ZIO HTTP Server <<<")
    } yield ()
  }
}
