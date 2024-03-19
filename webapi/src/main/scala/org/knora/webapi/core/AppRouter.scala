/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko
import zio.*

import org.knora.webapi.core
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.responders.v2.ResourceUtilV2
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.settings.APPLICATION_MANAGER_ACTOR_NAME
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache

import pekko.actor.{ActorRef, Props}
import pekko.routing.RoundRobinPool

final case class AppRouter private (system: pekko.actor.ActorSystem, ref: ActorRef)

object AppRouter {
  val layer: ZLayer[
    pekko.actor.ActorSystem & CardinalityHandler & CardinalityService & ConstructResponseUtilV2 & MessageRelay &
      OntologyCache & OntologyHelpers & OntologyRepo & PermissionUtilADM & ResourceUtilV2 & StandoffTagUtilV2,
    Nothing,
    AppRouter,
  ] =
    ZLayer {
      for {
        system       <- ZIO.service[pekko.actor.ActorSystem]
        messageRelay <- ZIO.service[MessageRelay]
        runtime <-
          ZIO.runtime[
            CardinalityHandler & CardinalityService & ConstructResponseUtilV2 & OntologyCache & OntologyHelpers &
              OntologyRepo & PermissionUtilADM & ResourceUtilV2 & StandoffTagUtilV2,
          ]
        ref = system.actorOf(
                Props(core.actors.RoutingActor(messageRelay, runtime)).withRouter(new RoundRobinPool(1_000)),
                name = APPLICATION_MANAGER_ACTOR_NAME,
              )
      } yield AppRouter(system, ref)
    }.tap(_ => ZIO.logInfo(">>> AppRouter Initialized <<<"))
}
