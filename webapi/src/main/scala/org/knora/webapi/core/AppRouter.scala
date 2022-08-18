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
import akka.pattern._
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
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.messages.util.KnoraSystemInstances
import akka.util.Timeout
import org.knora.webapi.messages.v2.responder.SuccessResponseV2

@accessible
trait AppRouter {
  val ref: ActorRef
  val populateOntologyCaches: UIO[Unit]
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

        val ref: ActorRef = system.actorOf(
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

        /* Calls into the OntologyResponderV2 to initiate loading of the ontologies into the cache. */
        val populateOntologyCaches: UIO[Unit] = {

          val request = LoadOntologiesRequestV2(requestingUser = KnoraSystemInstances.Users.SystemUser)
          val timeout = Timeout(new scala.concurrent.duration.FiniteDuration(3, scala.concurrent.duration.SECONDS))

          for {
            response <- ZIO.fromFuture(_ => (ref.ask(request)(timeout)).mapTo[SuccessResponseV2]).orDie
            _        <- ZIO.logInfo(response.message)
          } yield ()
        }
      }
    }.tap(_ => ZIO.logInfo(">>> AppRouter Initialized <<<"))
}
