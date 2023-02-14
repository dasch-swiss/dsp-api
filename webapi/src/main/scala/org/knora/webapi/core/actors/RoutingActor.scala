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
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsResponderRequestADM
import org.knora.webapi.messages.admin.responder.storesmessages.StoreResponderRequestADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersResponderRequestADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceRequest
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreRequest
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v1.responder.ckanmessages.CkanResponderRequestV1
import org.knora.webapi.messages.v1.responder.listmessages.ListsResponderRequestV1
import org.knora.webapi.messages.v1.responder.ontologymessages.OntologyResponderRequestV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectsResponderRequestV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourcesResponderRequestV1
import org.knora.webapi.messages.v1.responder.searchmessages.SearchResponderRequestV1
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffResponderRequestV1
import org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1
import org.knora.webapi.messages.v1.responder.valuemessages.ValuesResponderRequestV1
import org.knora.webapi.messages.v2.responder.listsmessages.ListsResponderRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologiesResponderRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesResponderRequestV2
import org.knora.webapi.messages.v2.responder.searchmessages.SearchResponderRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffResponderRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValuesResponderRequestV2
import org.knora.webapi.responders.ActorDeps
import org.knora.webapi.responders.admin.ProjectsResponderADM
import org.knora.webapi.responders.admin.StoresResponderADM
import org.knora.webapi.responders.admin.UsersResponderADM
import org.knora.webapi.responders.v1.CkanResponderV1
import org.knora.webapi.responders.v1.ListsResponderV1
import org.knora.webapi.responders.v1.OntologyResponderV1
import org.knora.webapi.responders.v1.ProjectsResponderV1
import org.knora.webapi.responders.v1.ResourcesResponderV1
import org.knora.webapi.responders.v1.SearchResponderV1
import org.knora.webapi.responders.v1.StandoffResponderV1
import org.knora.webapi.responders.v1.UsersResponderV1
import org.knora.webapi.responders.v1.ValuesResponderV1
import org.knora.webapi.responders.v2.ListsResponderV2
import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.responders.v2.ResourcesResponderV2
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.responders.v2.StandoffResponderV2
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.settings.CacheServiceSettings
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.util.ActorUtil

final case class RoutingActor(
  cacheServiceManager: CacheServiceManager,
  iiifServiceManager: IIIFServiceManager,
  triplestoreManager: TriplestoreServiceManager,
  appConfig: AppConfig,
  messageRelay: MessageRelay,
  implicit val runtime: zio.Runtime[CardinalityService]
) extends Actor {

  private val log: Logger                                 = Logger(this.getClass)
  private val actorDeps: ActorDeps                        = ActorDeps(context.system, self, appConfig.defaultTimeoutAsDuration)
  private val cacheServiceSettings: CacheServiceSettings  = new CacheServiceSettings(appConfig)
  private val responderData: ResponderData                = ResponderData(actorDeps, appConfig)
  private implicit val executionContext: ExecutionContext = actorDeps.executionContext

  // V1 responders
  private val ckanResponderV1: CkanResponderV1           = new CkanResponderV1(responderData)
  private val resourcesResponderV1: ResourcesResponderV1 = new ResourcesResponderV1(responderData)
  private val valuesResponderV1: ValuesResponderV1       = new ValuesResponderV1(responderData)
  private val standoffResponderV1: StandoffResponderV1   = new StandoffResponderV1(responderData)
  private val usersResponderV1: UsersResponderV1         = new UsersResponderV1(responderData)
  private val listsResponderV1: ListsResponderV1         = new ListsResponderV1(responderData)
  private val searchResponderV1: SearchResponderV1       = new SearchResponderV1(responderData)
  private val ontologyResponderV1: OntologyResponderV1   = new OntologyResponderV1(responderData)
  private val projectsResponderV1: ProjectsResponderV1   = ProjectsResponderV1(actorDeps)

  // V2 responders
  private val ontologiesResponderV2: OntologyResponderV2 = OntologyResponderV2(responderData, runtime)
  private val searchResponderV2: SearchResponderV2       = new SearchResponderV2(responderData)
  private val resourcesResponderV2: ResourcesResponderV2 = new ResourcesResponderV2(responderData)
  private val valuesResponderV2: ValuesResponderV2       = new ValuesResponderV2(responderData)
  private val standoffResponderV2: StandoffResponderV2   = new StandoffResponderV2(responderData)
  private val listsResponderV2: ListsResponderV2         = new ListsResponderV2(responderData)

  // Admin responders
  private val projectsResponderADM: ProjectsResponderADM = ProjectsResponderADM(actorDeps, cacheServiceSettings)
  private val storeResponderADM: StoresResponderADM      = new StoresResponderADM(responderData)
  private val usersResponderADM: UsersResponderADM       = new UsersResponderADM(responderData)

  def receive: Receive = {

    // V1 request messages
    case ckanResponderRequestV1: CkanResponderRequestV1 =>
      ActorUtil.future2Message(sender(), ckanResponderV1.receive(ckanResponderRequestV1), log)
    case resourcesResponderRequestV1: ResourcesResponderRequestV1 =>
      ActorUtil.future2Message(sender(), resourcesResponderV1.receive(resourcesResponderRequestV1), log)
    case valuesResponderRequestV1: ValuesResponderRequestV1 =>
      ActorUtil.future2Message(sender(), valuesResponderV1.receive(valuesResponderRequestV1), log)
    case listsResponderRequestV1: ListsResponderRequestV1 =>
      ActorUtil.future2Message(sender(), listsResponderV1.receive(listsResponderRequestV1), log)
    case searchResponderRequestV1: SearchResponderRequestV1 =>
      ActorUtil.future2Message(sender(), searchResponderV1.receive(searchResponderRequestV1), log)
    case ontologyResponderRequestV1: OntologyResponderRequestV1 =>
      ActorUtil.future2Message(sender(), ontologyResponderV1.receive(ontologyResponderRequestV1), log)
    case standoffResponderRequestV1: StandoffResponderRequestV1 =>
      ActorUtil.future2Message(sender(), standoffResponderV1.receive(standoffResponderRequestV1), log)
    case usersResponderRequestV1: UsersResponderRequestV1 =>
      ActorUtil.future2Message(sender(), usersResponderV1.receive(usersResponderRequestV1), log)
    case projectsResponderRequestV1: ProjectsResponderRequestV1 =>
      ActorUtil.future2Message(sender(), projectsResponderV1.receive(projectsResponderRequestV1), log)

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
    case listsResponderRequestV2: ListsResponderRequestV2 =>
      ActorUtil.future2Message(sender(), listsResponderV2.receive(listsResponderRequestV2), log)

    // Admin request messages
    case projectsResponderRequestADM: ProjectsResponderRequestADM =>
      ActorUtil.future2Message(sender(), projectsResponderADM.receive(projectsResponderRequestADM), log)
    case storeResponderRequestADM: StoreResponderRequestADM =>
      ActorUtil.future2Message(sender(), storeResponderADM.receive(storeResponderRequestADM), log)
    case usersResponderRequestADM: UsersResponderRequestADM =>
      ActorUtil.future2Message(sender(), usersResponderADM.receive(usersResponderRequestADM), log)
    case msg: CacheServiceRequest =>
      ActorUtil.zio2Message(sender(), cacheServiceManager.receive(msg), log, runtime)
    case msg: IIIFRequest => ActorUtil.zio2Message(sender(), iiifServiceManager.receive(msg), log, runtime)
    case msg: TriplestoreRequest =>
      ActorUtil.zio2Message(sender(), triplestoreManager.receive(msg), log, runtime)
    case req: ResponderRequest => UnsafeZioRun.runOrThrow(messageRelay.ask(req))

    case other =>
      throw UnexpectedMessageException(
        s"RoutingActor received an unexpected message $other of type ${other.getClass.getCanonicalName}"
      )
  }
}
