/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core.actors

import akka.actor.{Actor, ActorSystem}
import com.typesafe.scalalogging.Logger
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
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.searchmessages.SearchResponderRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffResponderRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValuesResponderRequestV2
import org.knora.webapi.responders.admin._
import org.knora.webapi.responders.v1._
import org.knora.webapi.responders.v2._
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoService
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.settings.CacheServiceSettings
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.util.ActorUtil

import scala.concurrent.{ExecutionContext, Future}

class RoutingActor(
  cacheServiceManager: CacheServiceManager,
  iiifServiceManager: IIIFServiceManager,
  triplestoreManager: TriplestoreServiceManager,
  appConfig: AppConfig,
  runtime: zio.Runtime[RestResourceInfoService]
) extends Actor {

  implicit val system: ActorSystem = context.system
  val log: Logger                  = Logger(this.getClass)

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
  val responderData: ResponderData = ResponderData(system, self, appConfig, cacheServiceSettings)

  // V1 responders
  val ckanResponderV1: CkanResponderV1           = new CkanResponderV1(responderData)
  val resourcesResponderV1: ResourcesResponderV1 = new ResourcesResponderV1(responderData)
  val valuesResponderV1: ValuesResponderV1       = new ValuesResponderV1(responderData)
  val standoffResponderV1: StandoffResponderV1   = new StandoffResponderV1(responderData)
  val usersResponderV1: UsersResponderV1         = new UsersResponderV1(responderData)
  val listsResponderV1: ListsResponderV1         = new ListsResponderV1(responderData)
  val searchResponderV1: SearchResponderV1       = new SearchResponderV1(responderData)
  val ontologyResponderV1: OntologyResponderV1   = new OntologyResponderV1(responderData)
  val projectsResponderV1: ProjectsResponderV1   = new ProjectsResponderV1(responderData)

  // V2 responders
  val ontologiesResponderV2: OntologyResponderV2 = new OntologyResponderV2(responderData)
  val searchResponderV2: SearchResponderV2       = new SearchResponderV2(responderData)
  val resourcesResponderV2: ResourcesResponderV2 = new ResourcesResponderV2(responderData)
  val valuesResponderV2: ValuesResponderV2       = new ValuesResponderV2(responderData)
  val standoffResponderV2: StandoffResponderV2   = new StandoffResponderV2(responderData)
  val listsResponderV2: ListsResponderV2         = new ListsResponderV2(responderData)

  // Admin responders
  val groupsResponderADM: GroupsResponderADM           = new GroupsResponderADM(responderData)
  val listsResponderADM: ListsResponderADM             = new ListsResponderADM(responderData)
  val permissionsResponderADM: PermissionsResponderADM = new PermissionsResponderADM(responderData)
  val projectsResponderADM: ProjectsResponderADM       = new ProjectsResponderADM(responderData)
  val storeResponderADM: StoresResponderADM            = new StoresResponderADM(responderData)
  val usersResponderADM: UsersResponderADM             = new UsersResponderADM(responderData)
  val sipiRouterADM: SipiResponderADM                  = new SipiResponderADM(responderData)

  private def future2Message(fut: Future[Any]): Unit = ActorUtil.future2Message(sender(), fut, log)

  def receive: Receive = {
    // V1 request messages
    case m: CkanResponderRequestV1      => future2Message(ckanResponderV1.receive(m))
    case m: ResourcesResponderRequestV1 => future2Message(resourcesResponderV1.receive(m))
    case m: ValuesResponderRequestV1    => future2Message(valuesResponderV1.receive(m))
    case m: ListsResponderRequestV1     => future2Message(listsResponderV1.receive(m))
    case m: SearchResponderRequestV1    => future2Message(searchResponderV1.receive(m))
    case m: OntologyResponderRequestV1  => future2Message(ontologyResponderV1.receive(m))
    case m: StandoffResponderRequestV1  => future2Message(standoffResponderV1.receive(m))
    case m: UsersResponderRequestV1     => future2Message(usersResponderV1.receive(m))
    case m: ProjectsResponderRequestV1  => future2Message(projectsResponderV1.receive(m))

    // V2 request messages
    case m: OntologiesResponderRequestV2 => future2Message(ontologiesResponderV2.receive(m))
    case m: SearchResponderRequestV2     => future2Message(searchResponderV2.receive(m))
    case m: ResourcesResponderRequestV2  => routeResourcesResponderRequestV2(m)
    case m: ValuesResponderRequestV2     => future2Message(valuesResponderV2.receive(m))
    case m: StandoffResponderRequestV2   => future2Message(standoffResponderV2.receive(m))
    case m: ListsResponderRequestV2      => future2Message(listsResponderV2.receive(m))

    // Admin request messages
    case m: GroupsResponderRequestADM      => future2Message(groupsResponderADM.receive(m))
    case m: ListsResponderRequestADM       => future2Message(listsResponderADM.receive(m))
    case m: PermissionsResponderRequestADM => future2Message(permissionsResponderADM.receive(m))
    case m: ProjectsResponderRequestADM    => future2Message(projectsResponderADM.receive(m))
    case m: StoreResponderRequestADM       => future2Message(storeResponderADM.receive(m))
    case m: UsersResponderRequestADM       => future2Message(usersResponderADM.receive(m))
    case m: SipiResponderRequestADM        => future2Message(sipiRouterADM.receive(m))
    case m: CacheServiceRequest            => ActorUtil.zio2Message(sender(), cacheServiceManager.receive(m), log, runtime)
    case m: IIIFRequest                    => ActorUtil.zio2Message(sender(), iiifServiceManager.receive(m), log, runtime)
    case m: TriplestoreRequest             => ActorUtil.zio2Message(sender(), triplestoreManager.receive(m), log, runtime)

    case other =>
      throw UnexpectedMessageException(
        s"RoutingActor received an unexpected message $other of type ${other.getClass.getCanonicalName}"
      )
  }
  def routeResourcesResponderRequestV2(message: ResourcesResponderRequestV2) = message match {
    case m: ResourcesGetRequestV2              => future2Message(resourcesResponderV2.getResourcesV2(m))
    case m: ResourcesPreviewGetRequestV2       => future2Message(resourcesResponderV2.getResourcePreviewV2(m))
    case m: ResourceTEIGetRequestV2            => future2Message(resourcesResponderV2.getResourceAsTeiV2(m))
    case m: CreateResourceRequestV2            => future2Message(resourcesResponderV2.createResourceV2(m))
    case m: UpdateResourceMetadataRequestV2    => future2Message(resourcesResponderV2.updateResourceMetadataV2(m))
    case m: DeleteOrEraseResourceRequestV2     => future2Message(resourcesResponderV2.deleteOrEraseResourceV2(m))
    case m: GraphDataGetRequestV2              => future2Message(resourcesResponderV2.getGraphDataResponseV2(m))
    case m: ResourceVersionHistoryGetRequestV2 => future2Message(resourcesResponderV2.getResourceHistoryV2(m))
    case m: ResourceIIIFManifestGetRequestV2   => future2Message(resourcesResponderV2.getIIIFManifestV2(m))
    case m: ResourceHistoryEventsGetRequestV2  => future2Message(resourcesResponderV2.getResourceHistoryEvents(m))
    case m: ProjectResourcesWithHistoryGetRequestV2 =>
      future2Message(resourcesResponderV2.getProjectResourceHistoryEvents(m))
    case m: GetResourceInfoRequestV2 =>
      ActorUtil.zio2Message(
        sender(),
        RestResourceInfoService.findByProjectAndResourceClass(m.projectIri, m.resourceClass),
        log,
        runtime
      )
  }
}
