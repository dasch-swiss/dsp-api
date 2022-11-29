/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zhttp.service.Server
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.routing.IndexApp
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoute

object HttpServerWithZIOHttp {

  val layer: ZLayer[ResourceInfoRoute with AppConfig, Nothing, Unit] =
    ZLayer {
      for {
        appConfig <- ZIO.service[AppConfig]
        riRoute   <- ZIO.service[ResourceInfoRoute].map(_.route)
        port       = appConfig.knoraApi.externalZioPort
        routes     = IndexApp() ++ riRoute
        _         <- Server.start(port, routes).forkDaemon
        _         <- ZIO.logInfo(s">>> Acquire ZIO HTTP Server on port $port<<<")
      } yield ()
    }
}
