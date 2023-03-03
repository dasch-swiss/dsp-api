/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import akka.actor.ActorRef
import akka.actor.Props
import akka.pattern._
import akka.routing.RoundRobinPool
import akka.util.Timeout
import zio._
import zio.macros.accessible

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.ValueUtilV1
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.responders.v2.ResourceUtilV2
import org.knora.webapi.settings._
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.iiif.IIIFServiceManager

@accessible
trait AppRouter {
  val system: akka.actor.ActorSystem
  val ref: ActorRef
  val populateOntologyCaches: Task[Unit]
}

object AppRouter {
  val layer: ZLayer[
    core.ActorSystem
      with AppConfig
      with CacheServiceManager
      with CardinalityService
      with IIIFServiceManager
      with MessageRelay
      with PermissionUtilADM
      with ResourceUtilV2
      with StandoffTagUtilV2
      with ValueUtilV1,
    Nothing,
    AppRouter
  ] =
    ZLayer {
      for {
        as                  <- ZIO.service[core.ActorSystem]
        cacheServiceManager <- ZIO.service[CacheServiceManager]
        iiifServiceManager  <- ZIO.service[IIIFServiceManager]
        appConfig           <- ZIO.service[AppConfig]
        messageRelay        <- ZIO.service[MessageRelay]
        runtime <-
          ZIO.runtime[
            CardinalityService with PermissionUtilADM with ResourceUtilV2 with StandoffTagUtilV2 with ValueUtilV1
          ]
      } yield new AppRouter {
        implicit val system: akka.actor.ActorSystem = as.system

        val ref: ActorRef = system.actorOf(
          Props(
            core.actors.RoutingActor(
              cacheServiceManager,
              iiifServiceManager,
              appConfig,
              messageRelay,
              runtime
            )
          ).withRouter(new RoundRobinPool(1_000)),
          name = APPLICATION_MANAGER_ACTOR_NAME
        )

        /* Calls into the OntologyResponderV2 to initiate loading of the ontologies into the cache. */
        val populateOntologyCaches: Task[Unit] = {

          val request = LoadOntologiesRequestV2(requestingUser = KnoraSystemInstances.Users.SystemUser)
          val timeout = Timeout(new scala.concurrent.duration.FiniteDuration(60, scala.concurrent.duration.SECONDS))

          for {
            response <- ZIO.fromFuture(_ => ref.ask(request)(timeout).mapTo[SuccessResponseV2])
            _        <- ZIO.logInfo(response.message)
          } yield ()
        }
      }
    }.tap(_ => ZIO.logInfo(">>> AppRouter Initialized <<<"))
}
