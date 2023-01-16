/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core
import zhttp.http._
import zhttp.service.Server
import zio.ZLayer
import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.routing.admin.ProjectsRouteZ
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoute

object HttpServerZ {

  private val apiRoutes: ZIO[ProjectsRouteZ with ResourceInfoRoute, Nothing, HttpApp[StringFormatter, Nothing]] = for {
    projectsRoute <- ZIO.service[ProjectsRouteZ].map(_.route)
    riRoute       <- ZIO.service[ResourceInfoRoute].map(_.route)
  } yield projectsRoute ++ riRoute

  val layer: ZLayer[ResourceInfoRoute with ProjectsRouteZ with AppConfig with StringFormatter, Nothing, Unit] = ZLayer {
    for {
      port   <- ZIO.service[AppConfig].map(_.knoraApi.externalZioPort)
      routes <- apiRoutes
      _      <- Server.start(port, routes).forkDaemon
      _      <- ZIO.logInfo(">>> Acquire ZIO HTTP Server <<<")
    } yield ()
  }
}
