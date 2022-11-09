/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import akka.http.scaladsl.Http
import zio._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core
import org.knora.webapi.routing.ApiRoutes
import org.knora.webapi.routingz.ApiZRoutes

/**
 * The ZIO-Http based HTTP server
 */
trait HttpZServer {
  val serverBinding: Http.ServerBinding
}

object HttpZServer {
  val layer: ZLayer[AppConfig & ApiZRoutes, Nothing, HttpZServer] =
    ZLayer.scoped {
      for {
        config    <- ZIO.service[AppConfig]
        apiRoutes <- ZIO.service[ApiRoutes]

        binding <- {

          ZIO.acquireRelease {
            ZIO
              .fromFuture(_ =>
                Http().newServerAt(config.knoraApi.internalHost, config.knoraApi.internalPort).bind(apiRoutes.routes)
              )
              .zipLeft(ZIO.logInfo(">>> Acquire HTTP Server <<<"))
              .orDie
          } { serverBinding =>
            ZIO
              .fromFuture(_ =>
                serverBinding.terminate(
                  new scala.concurrent.duration.FiniteDuration(1, scala.concurrent.duration.MILLISECONDS)
                )
              )
              .zipLeft(ZIO.logInfo(">>> Release HTTP Server <<<"))
              .orDie
          }
        }
      } yield HttpServerImpl(binding)
    }

  private final case class HttpServerImpl(binding: Http.ServerBinding) extends HttpServer { self =>
    val serverBinding: Http.ServerBinding = self.binding
  }
}
