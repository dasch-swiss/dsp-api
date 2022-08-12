/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.LoggingReceive
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.settings._
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.settings.CacheServiceSettings
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.util.ActorUtil._
import zio._
import zio.macros.accessible

import scala.concurrent.ExecutionContext

@accessible
trait AppRouter {
  val ref: ActorRef
}

object AppRouter {
  val layer: ZLayer[
    core.ActorSystem with CacheServiceManager with IIIFServiceManager with TriplestoreServiceManager with AppConfig,
    Nothing,
    AppRouter
  ] =
    ZLayer {
      for {
        as                        <- ZIO.service[core.ActorSystem]
        cacheServiceManager       <- ZIO.service[CacheServiceManager]
        iiifServiceManager        <- ZIO.service[IIIFServiceManager]
        triplestoreServiceManager <- ZIO.service[TriplestoreServiceManager]
        appConfig                 <- ZIO.service[AppConfig]
      } yield new AppRouter { self =>
        implicit lazy val system: akka.actor.ActorSystem =
          as.system

        implicit val executionContext: ExecutionContext = system.dispatcher

        override val ref: ActorRef = system.actorOf(
          Props(
            new core.actors.RoutingActor(
              cacheServiceManager,
              iiifServiceManager,
              triplestoreServiceManager,
              appConfig
            )
          ),
          name = APPLICATION_MANAGER_ACTOR_NAME
        )
      }
    }.tap(_ => ZIO.logInfo(">>> AppRouter Initialized <<<"))
}
