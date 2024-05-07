/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core.actors

import org.apache.pekko.actor.Actor
import zio.*

import dsp.errors.UnexpectedMessageException
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.responders.v2.ResourceUtilV2
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.util.ActorUtil

final case class RoutingActor(messageRelay: MessageRelay)(implicit
  val runtime: Runtime[
    CardinalityHandler & CardinalityService & ConstructResponseUtilV2 & OntologyCache & OntologyHelpers & OntologyRepo &
      PermissionUtilADM & ResourceUtilV2 & StandoffTagUtilV2,
  ],
) extends Actor {

  def receive: Receive = {
    case msg: RelayedMessage => ActorUtil.zio2Message(sender(), messageRelay.ask[Any](msg))
    case other =>
      throw UnexpectedMessageException(
        s"RoutingActor received an unexpected message $other of type ${other.getClass.getCanonicalName}",
      )
  }
}
