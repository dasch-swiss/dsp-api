/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio._
import zio.http._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoute

object HttpServerZ {

  private val apiRoutes: URIO[ResourceInfoRoute, HttpApp[Any, Nothing]] = for {
    riRoute <- ZIO.serviceWith[ResourceInfoRoute](_.route)
  } yield riRoute

  val layer: ZLayer[ResourceInfoRoute with AppConfig, Nothing, Unit] = ZLayer {
    for {
      port                <- ZIO.serviceWith[AppConfig](_.knoraApi.externalZioPort)
      routes              <- apiRoutes
      routesWithMiddleware = routes
      _                   <- Server.serve(routesWithMiddleware).provide(Server.defaultWithPort(port)).forkDaemon
      _                   <- ZIO.logInfo(">>> Acquire ZIO HTTP Server <<<")
    } yield ()
  }
}
