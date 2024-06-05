/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.Props
import org.apache.pekko.routing.RoundRobinPool
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.core
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.responders.v2.ResourceUtilV2
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.OntologyCacheHelpers
import org.knora.webapi.responders.v2.ontology.OntologyTriplestoreHelpers
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache

final case class AppRouter private (system: pekko.actor.ActorSystem, ref: ActorRef)

object AppRouter {

  val layer: ZLayer[
    pekko.actor.ActorSystem & CardinalityHandler & CardinalityService & ConstructResponseUtilV2 & MessageRelay &
      OntologyCache & OntologyTriplestoreHelpers & OntologyCacheHelpers & OntologyRepo & PermissionUtilADM &
      ResourceUtilV2 & StandoffTagUtilV2,
    Nothing,
    AppRouter,
  ] =
    ZLayer
      .fromZIO(
        for {
          system       <- ZIO.service[pekko.actor.ActorSystem]
          messageRelay <- ZIO.service[MessageRelay]
          runtime <-
            ZIO.runtime[
              CardinalityHandler & CardinalityService & ConstructResponseUtilV2 & OntologyCache &
                OntologyTriplestoreHelpers & OntologyCacheHelpers & OntologyRepo & PermissionUtilADM & ResourceUtilV2 &
                StandoffTagUtilV2,
            ]
          ref = system.actorOf(
                  Props(core.actors.RoutingActor(messageRelay)(runtime)).withRouter(new RoundRobinPool(1_000)),
                  "applicationManager",
                )
        } yield AppRouter(system, ref),
      )
      .tap(_ => ZIO.logInfo(">>> AppRouter Initialized <<<"))
}
