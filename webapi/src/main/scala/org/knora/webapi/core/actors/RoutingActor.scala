/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core.actors

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext

import dsp.errors.UnexpectedMessageException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsResponderRequestADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListsResponderRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsResponderRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsResponderRequestADM
import org.knora.webapi.messages.admin.responder.sipimessages.SipiResponderRequestADM
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
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.responders.admin.ListsResponderADM
import org.knora.webapi.responders.admin.PermissionsResponderADM
import org.knora.webapi.responders.admin.ProjectsResponderADM
import org.knora.webapi.responders.admin.SipiResponderADM
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
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.settings.CacheServiceSettings
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.util.ActorUtil

class RoutingActor(
  cacheServiceManager: CacheServiceManager,
  iiifServiceManager: IIIFServiceManager,
  triplestoreManager: TriplestoreServiceManager,
  appConfig: AppConfig,
  runtime: zio.Runtime[Any]
) extends Actor {

  implicit val system: ActorSystem = context.system
  val log: Logger                  = Logger(this.getClass())

  /**
   * The Cache Service's configuration.
   */
  implicit val cacheServiceSettings: CacheServiceSettings = new CacheServiceSettings(appConfig)

  /**
   * Provides the default global execution context
   */
  implicit val executionContext: ExecutionContext = context.dispatcher

  /**
   * Data used in responders.
   */
  val responderData: ResponderData = ResponderData(system, self, cacheServiceSettings)

  // V1 responders
  val ckanResponderV1: CkanResponderV1           = new CkanResponderV1(responderData, appConfig)
  val resourcesResponderV1: ResourcesResponderV1 = new ResourcesResponderV1(responderData, appConfig)
  val valuesResponderV1: ValuesResponderV1       = new ValuesResponderV1(responderData, appConfig)
  val standoffResponderV1: StandoffResponderV1   = new StandoffResponderV1(responderData, appConfig)
  val usersResponderV1: UsersResponderV1         = new UsersResponderV1(responderData, appConfig)
  val listsResponderV1: ListsResponderV1         = new ListsResponderV1(responderData, appConfig)
  val searchResponderV1: SearchResponderV1       = new SearchResponderV1(responderData, appConfig)
  val ontologyResponderV1: OntologyResponderV1   = new OntologyResponderV1(responderData, appConfig)
  val projectsResponderV1: ProjectsResponderV1   = new ProjectsResponderV1(responderData, appConfig)

  // V2 responders
  val ontologiesResponderV2: OntologyResponderV2 = new OntologyResponderV2(responderData, appConfig)
  val searchResponderV2: SearchResponderV2       = new SearchResponderV2(responderData, appConfig)
  val resourcesResponderV2: ResourcesResponderV2 = new ResourcesResponderV2(responderData, appConfig)
  val valuesResponderV2: ValuesResponderV2       = new ValuesResponderV2(responderData, appConfig)
  val standoffResponderV2: StandoffResponderV2   = new StandoffResponderV2(responderData, appConfig)
  val listsResponderV2: ListsResponderV2         = new ListsResponderV2(responderData, appConfig)

  // Admin responders
  val groupsResponderADM: GroupsResponderADM           = new GroupsResponderADM(responderData, appConfig)
  val listsResponderADM: ListsResponderADM             = new ListsResponderADM(responderData, appConfig)
  val permissionsResponderADM: PermissionsResponderADM = new PermissionsResponderADM(responderData, appConfig)
  val projectsResponderADM: ProjectsResponderADM       = new ProjectsResponderADM(responderData, appConfig)
  val storeResponderADM: StoresResponderADM            = new StoresResponderADM(responderData, appConfig)
  val usersResponderADM: UsersResponderADM             = new UsersResponderADM(responderData, appConfig)
  val sipiRouterADM: SipiResponderADM                  = new SipiResponderADM(responderData, appConfig)

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
    case groupsResponderRequestADM: GroupsResponderRequestADM =>
      ActorUtil.future2Message(sender(), groupsResponderADM.receive(groupsResponderRequestADM), log)
    case listsResponderRequest: ListsResponderRequestADM =>
      ActorUtil.future2Message(sender(), listsResponderADM.receive(listsResponderRequest), log)
    case permissionsResponderRequestADM: PermissionsResponderRequestADM =>
      ActorUtil.future2Message(sender(), permissionsResponderADM.receive(permissionsResponderRequestADM), log)
    case projectsResponderRequestADM: ProjectsResponderRequestADM =>
      ActorUtil.future2Message(sender(), projectsResponderADM.receive(projectsResponderRequestADM), log)
    case storeResponderRequestADM: StoreResponderRequestADM =>
      ActorUtil.future2Message(sender(), storeResponderADM.receive(storeResponderRequestADM), log)
    case usersResponderRequestADM: UsersResponderRequestADM =>
      ActorUtil.future2Message(sender(), usersResponderADM.receive(usersResponderRequestADM), log)
    case sipiResponderRequestADM: SipiResponderRequestADM =>
      ActorUtil.future2Message(sender(), sipiRouterADM.receive(sipiResponderRequestADM), log)
    case msg: CacheServiceRequest =>
      ActorUtil.zio2Message(sender(), cacheServiceManager.receive(msg), appConfig, log, runtime)
    case msg: IIIFRequest => ActorUtil.zio2Message(sender(), iiifServiceManager.receive(msg), appConfig, log, runtime)
    case msg: TriplestoreRequest =>
      ActorUtil.zio2Message(sender(), triplestoreManager.receive(msg), appConfig, log, runtime)

    case other =>
      throw UnexpectedMessageException(
        s"RoutingActor received an unexpected message $other of type ${other.getClass.getCanonicalName}"
      )
  }

}
