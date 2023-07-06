/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import akka.actor.ActorRef
import akka.actor.Props
import akka.routing.RoundRobinPool
import zio._
import zio.macros.accessible

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.ValueUtilV1
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.responders.v2.ResourceUtilV2
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.settings.APPLICATION_MANAGER_ACTOR_NAME
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache

@accessible
trait AppRouter {
  val system: akka.actor.ActorSystem
  val ref: ActorRef
}

object AppRouter {
  val layer: ZLayer[
    core.ActorSystem
      with AppConfig
      with CardinalityHandler
      with CardinalityService
      with ConstructResponseUtilV2
      with MessageRelay
      with OntologyCache
      with OntologyHelpers
      with OntologyRepo
      with PermissionUtilADM
      with ResourceUtilV2
      with StandoffTagUtilV2
      with ValueUtilV1,
    Nothing,
    AppRouter
  ] =
    ZLayer {
      for {
        as           <- ZIO.service[core.ActorSystem]
        appConfig    <- ZIO.service[AppConfig]
        messageRelay <- ZIO.service[MessageRelay]
        runtime <-
          ZIO.runtime[
            CardinalityHandler
              with CardinalityService
              with ConstructResponseUtilV2
              with OntologyCache
              with OntologyHelpers
              with OntologyRepo
              with PermissionUtilADM
              with ResourceUtilV2
              with StandoffTagUtilV2
              with ValueUtilV1
          ]
      } yield new AppRouter {
        implicit val system: akka.actor.ActorSystem = as.system

        val ref: ActorRef = system.actorOf(
          Props(
            core.actors.RoutingActor(
              appConfig,
              messageRelay,
              runtime
            )
          ).withRouter(new RoundRobinPool(1_000)),
          name = APPLICATION_MANAGER_ACTOR_NAME
        )
      }
    }.tap(_ => ZIO.logInfo(">>> AppRouter Initialized <<<"))
}
