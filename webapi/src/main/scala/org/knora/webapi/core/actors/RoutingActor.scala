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
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceRequest
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreRequest
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.util.ValueUtilV1
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourcesResponderRequestV1
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffResponderRequestV1
import org.knora.webapi.messages.v1.responder.valuemessages.ValuesResponderRequestV1
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologiesResponderRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesResponderRequestV2
import org.knora.webapi.messages.v2.responder.searchmessages.SearchResponderRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffResponderRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValuesResponderRequestV2
import org.knora.webapi.responders.ActorDeps
import org.knora.webapi.responders.v1._
import org.knora.webapi.responders.v2._
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.util.ActorUtil

final case class RoutingActor(
  cacheServiceManager: CacheServiceManager,
  iiifServiceManager: IIIFServiceManager,
  triplestoreManager: TriplestoreServiceManager,
  appConfig: AppConfig,
  messageRelay: MessageRelay,
  implicit val runtime: zio.Runtime[CardinalityService with StandoffTagUtilV2 with ValueUtilV1 with ResourceUtilV2]
) extends Actor {

  private val log: Logger                                 = Logger(this.getClass)
  private val actorDeps: ActorDeps                        = ActorDeps(context.system, self, appConfig.defaultTimeoutAsDuration)
  private val responderData: ResponderData                = ResponderData(actorDeps, appConfig)
  private implicit val executionContext: ExecutionContext = actorDeps.executionContext

  // V1 responders
  private val resourcesResponderV1: ResourcesResponderV1 = new ResourcesResponderV1(responderData, runtime)
  private val valuesResponderV1: ValuesResponderV1       = new ValuesResponderV1(responderData, runtime)
  private val standoffResponderV1: StandoffResponderV1   = new StandoffResponderV1(responderData)

  // V2 responders
  private val ontologiesResponderV2: OntologyResponderV2 = OntologyResponderV2(responderData, runtime)
  private val searchResponderV2: SearchResponderV2       = new SearchResponderV2(responderData, runtime)
  private val resourcesResponderV2: ResourcesResponderV2 = new ResourcesResponderV2(responderData, runtime)
  private val valuesResponderV2: ValuesResponderV2       = new ValuesResponderV2(responderData, runtime)
  private val standoffResponderV2: StandoffResponderV2   = new StandoffResponderV2(responderData, runtime)

  def receive: Receive = {
    // RelayedMessages have a corresponding MessageHandler registered with the MessageRelay
    case msg: RelayedMessage => ActorUtil.zio2Message(sender(), messageRelay.ask[Any](msg))

    // V1 request messages
    case resourcesResponderRequestV1: ResourcesResponderRequestV1 =>
      ActorUtil.future2Message(sender(), resourcesResponderV1.receive(resourcesResponderRequestV1), log)
    case valuesResponderRequestV1: ValuesResponderRequestV1 =>
      ActorUtil.future2Message(sender(), valuesResponderV1.receive(valuesResponderRequestV1), log)
    case standoffResponderRequestV1: StandoffResponderRequestV1 =>
      ActorUtil.future2Message(sender(), standoffResponderV1.receive(standoffResponderRequestV1), log)

    // V2 request messages
    case ontologiesResponderRequestV2: OntologiesResponderRequestV2 =>
      ActorUtil.future2Message(sender(), ontologiesResponderV2.receive(ontologiesResponderRequestV2), log)
    case searchResponderRequestV2: SearchResponderRequestV2 =>
      ActorUtil.future2Message(sender(), searchResponderV2.receive(searchResponderRequestV2), log)
    case resourcesResponderRequestV2: ResourcesResponderRequestV2 =>
      ActorUtil.future2Message(sender(), resourcesResponderV2.receive(resourcesResponderRequestV2), log)
    case valuesResponderRequestV2: ValuesResponderRequestV2 =>
      ActorUtil.future2Message(sender(), valuesResponderV2.receive(valuesResponderRequestV2), log)
    case standoffResponderRequestV2: StandoffResponderRequestV2 =>
      ActorUtil.future2Message(sender(), standoffResponderV2.receive(standoffResponderRequestV2), log)

    // Admin request messages
    case msg: CacheServiceRequest => ActorUtil.zio2Message(sender(), cacheServiceManager.receive(msg))
    case msg: IIIFRequest         => ActorUtil.zio2Message(sender(), iiifServiceManager.receive(msg))
    case msg: TriplestoreRequest  => ActorUtil.zio2Message(sender(), triplestoreManager.receive(msg))

    case other =>
      throw UnexpectedMessageException(
        s"RoutingActor received an unexpected message $other of type ${other.getClass.getCanonicalName}"
      )
  }
}
