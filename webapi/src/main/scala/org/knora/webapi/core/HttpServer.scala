/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import zio._

import scala.concurrent.ExecutionContext

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core

trait HttpServer {
  val actorSystem: UIO[akka.actor.ActorSystem]
  def start(routes: Route): ZIO[Scope, Nothing, Http.ServerBinding]
}

object HttpServer {

  val layer: ZLayer[core.ActorSystem with AppConfig, Nothing, HttpServer] =
    ZLayer { // ZLayer.scope;
      for {
        as     <- ZIO.service[core.ActorSystem]
        config <- ZIO.service[AppConfig]
      } yield new HttpServer {

        implicit val system: akka.actor.ActorSystem     = as.system
        implicit val materializer: Materializer         = Materializer.matFromSystem(system)
        implicit val executionContext: ExecutionContext = system.dispatcher

        val actorSystem: UIO[akka.actor.ActorSystem] = ZIO.succeed(as.system)

        def start(routes: Route): ZIO[Scope, Nothing, Http.ServerBinding] =
          ZIO.acquireRelease {
            ZIO
              .fromFuture(_ =>
                Http().newServerAt(config.knoraApi.internalHost, config.knoraApi.internalPort).bind(routes)
              )
              .tap(_ => ZIO.logInfo(">>> Acquire HTTP Server <<<"))
              .orDie
          } { serverBinding =>
            ZIO
              .fromFuture(_ =>
                serverBinding.terminate(
                  new scala.concurrent.duration.FiniteDuration(1, scala.concurrent.duration.SECONDS)
                )
              )
              .tap(_ => ZIO.logInfo(">>> Release HTTP Server and Actor System <<<"))
              .orDie
          }
      }
    }

  val actorSystem: ZIO[HttpServer, Nothing, akka.actor.ActorSystem] =
    ZIO.serviceWithZIO[HttpServer](_.actorSystem)

  def start(routes: Route): ZIO[Scope with HttpServer, Nothing, Http.ServerBinding] =
    ZIO.serviceWithZIO[HttpServer](_.start(routes))
}
