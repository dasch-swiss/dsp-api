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

/**
 * The Akka based HTTP server
 */
trait HttpServer {
  val serverBinding: Http.ServerBinding
}

object HttpServer {
  val layer: ZLayer[core.ActorSystem & AppConfig & ApiRoutes, Nothing, HttpServer] =
    ZLayer.scoped {
      for {
        as        <- ZIO.service[core.ActorSystem]
        config    <- ZIO.service[AppConfig]
        apiRoutes <- ZIO.service[ApiRoutes]
        binding <- {
          implicit val system: akka.actor.ActorSystem = as.system

          ZIO.acquireRelease {
            ZIO
              .fromFuture(_ =>
                Http().newServerAt(config.knoraApi.internalHost, config.knoraApi.internalPort).bind(apiRoutes.routes)
              )
              .tap(_ => ZIO.logInfo(">>> Acquire HTTP Server <<<"))
              .orDie
          } { serverBinding =>
            ZIO
              .fromFuture(_ =>
                serverBinding.terminate(
                  new scala.concurrent.duration.FiniteDuration(1, scala.concurrent.duration.MILLISECONDS)
                )
              )
              .tap(_ => ZIO.logInfo(">>> Release HTTP Server <<<"))
              .orDie
          }
        }
      } yield HttpServerImpl(binding)
    }

  private final case class HttpServerImpl(binding: Http.ServerBinding) extends HttpServer { self =>
    val serverBinding: Http.ServerBinding = self.binding
  }
}
