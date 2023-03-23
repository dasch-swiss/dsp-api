/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core.actors

import akka.actor.Actor
import zio._

import dsp.errors.UnexpectedMessageException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.ValueUtilV1
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.responders.v2._
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.util.ActorUtil

final case class RoutingActor(
  appConfig: AppConfig,
  messageRelay: MessageRelay,
  implicit val runtime: Runtime[
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
) extends Actor {
  def receive: Receive = {
    case msg: RelayedMessage => ActorUtil.zio2Message(sender(), messageRelay.ask[Any](msg))
    case other =>
      throw UnexpectedMessageException(
        s"RoutingActor received an unexpected message $other of type ${other.getClass.getCanonicalName}"
      )
  }
}
