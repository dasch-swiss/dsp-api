/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core.actors

import akka.actor.Actor
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext

import dsp.errors.UnexpectedMessageException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.util.ConstructResponseUtilV2
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.util.ValueUtilV1
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesResponderRequestV2
import org.knora.webapi.messages.v2.responder.searchmessages.SearchResponderRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffResponderRequestV2
import org.knora.webapi.responders.ActorDeps
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
  implicit val runtime: zio.Runtime[
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

  private val log: Logger                                 = Logger(this.getClass)
  private val actorDeps: ActorDeps                        = ActorDeps(context.system, self, appConfig.defaultTimeoutAsDuration)
  private val responderData: ResponderData                = ResponderData(actorDeps, appConfig)
  private implicit val executionContext: ExecutionContext = actorDeps.executionContext

  // V2 responders
  private val searchResponderV2: SearchResponderV2       = new SearchResponderV2(responderData, runtime)
  private val resourcesResponderV2: ResourcesResponderV2 = new ResourcesResponderV2(responderData, runtime)
  private val standoffResponderV2: StandoffResponderV2   = new StandoffResponderV2(responderData, runtime)

  def receive: Receive = {
    // RelayedMessages have a corresponding MessageHandler registered with the MessageRelay
    case msg: RelayedMessage => ActorUtil.zio2Message(sender(), messageRelay.ask[Any](msg))

    // V2 request messages
    case searchResponderRequestV2: SearchResponderRequestV2 =>
      ActorUtil.future2Message(sender(), searchResponderV2.receive(searchResponderRequestV2), log)
    case resourcesResponderRequestV2: ResourcesResponderRequestV2 =>
      ActorUtil.future2Message(sender(), resourcesResponderV2.receive(resourcesResponderRequestV2), log)
    case standoffResponderRequestV2: StandoffResponderRequestV2 =>
      ActorUtil.future2Message(sender(), standoffResponderV2.receive(standoffResponderRequestV2), log)

    case other =>
      throw UnexpectedMessageException(
        s"RoutingActor received an unexpected message $other of type ${other.getClass.getCanonicalName}"
      )
  }
}
