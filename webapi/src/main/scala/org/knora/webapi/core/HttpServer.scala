/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.event.LoggingReceive
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core
import org.knora.webapi.core.ActorSystem
import org.knora.webapi.core.AppRouter
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.responders.admin._
import org.knora.webapi.responders.v1._
import org.knora.webapi.responders.v2._
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.store.cache.settings.CacheServiceSettings
import org.knora.webapi.util.ActorUtil._
import zio._
import zio.macros.accessible

import scala.concurrent.ExecutionContext
import scala.util.Success

trait HttpServer {
  val actorSystem: UIO[akka.actor.ActorSystem]
  def start(routes: Route): ZIO[Scope, Nothing, Http.ServerBinding]
}

object HttpServer {

  val layer: ZLayer[core.ActorSystem with AppConfig, Nothing, HttpServer] =
    ZLayer.scoped {
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
