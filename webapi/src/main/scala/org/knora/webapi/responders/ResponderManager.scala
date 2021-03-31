/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem}
import akka.event.LoggingReceive
import org.knora.webapi.core.ActorMaker
import org.knora.webapi.feature.KnoraSettingsFeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsResponderRequestADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListsResponderRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsResponderRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsResponderRequestADM
import org.knora.webapi.messages.admin.responder.sipimessages.SipiResponderRequestADM
import org.knora.webapi.messages.admin.responder.storesmessages.StoreResponderRequestADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersResponderRequestADM
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
import org.knora.webapi.messages.v2.responder.metadatamessages.MetadataResponderRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologiesResponderRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesResponderRequestV2
import org.knora.webapi.messages.v2.responder.searchmessages.SearchResponderRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffResponderRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValuesResponderRequestV2
import org.knora.webapi.responders.admin._
import org.knora.webapi.responders.v1._
import org.knora.webapi.responders.v2._
import org.knora.webapi.responders.v2.resources.{ResourcesResponderFeatureFactoryV2, ResourcesResponderV2}
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl}
import org.knora.webapi.util.ActorUtil._

import scala.concurrent.ExecutionContext

/**
  * This actor receives messages representing client requests, and forwards them to pools of specialised actors
  * that it supervises.
  *
  * @param appActor the main application actor.
  */
class ResponderManager(appActor: ActorRef) extends Actor with ActorLogging {
  this: ActorMaker =>

  /**
    * The responder's Akka actor system.
    */
  protected implicit val system: ActorSystem = context.system

  private val settings: KnoraSettingsImpl = KnoraSettings(system)

  private val featureFactoryConfig: KnoraSettingsFeatureFactoryConfig =
    new KnoraSettingsFeatureFactoryConfig(settings)

  /**
    * The Akka actor system's execution context for futures.
    */
  protected implicit val executionContext: ExecutionContext =
    system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  /**
    * The responder data.
    */
  private val responderData = ResponderData(
    system = system,
    appActor = appActor
  )

  // A subclass can replace the standard responders with custom responders, e.g. for testing. To do this, it must
  // override one or more of the protected val members below representing responder classes. To construct a default
  // responder, a subclass can call one of the protected methods below.

  /**
    * Constructs the default [[CkanResponderV1]].
    */
  protected final def makeDefaultCkanResponderV1: CkanResponderV1 = new CkanResponderV1(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default Ckan responder.
    */
  protected lazy val ckanResponderV1: CkanResponderV1 = makeDefaultCkanResponderV1

  /**
    * Constructs the default [[ResourcesResponderV1]].
    */
  protected final def makeDefaultResourcesResponderV1: ResourcesResponderV1 = new ResourcesResponderV1(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default resources V1 responder.
    */
  protected lazy val resourcesResponderV1: ResourcesResponderV1 = makeDefaultResourcesResponderV1

  /**
    * Constructs the default [[ValuesResponderV1]].
    */
  protected final def makeDefaultValuesResponderV1: ValuesResponderV1 = new ValuesResponderV1(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default values V1 responder.
    */
  protected lazy val valuesResponderV1: ValuesResponderV1 = makeDefaultValuesResponderV1

  /**
    * Constructs the default [[StandoffResponderV1]].
    */
  protected final def makeDefaultStandoffResponderV1: StandoffResponderV1 = new StandoffResponderV1(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default standoff V1 responder.
    */
  protected lazy val standoffResponderV1: StandoffResponderV1 = makeDefaultStandoffResponderV1

  /**
    * Constructs the default [[UsersResponderV1]].
    */
  protected final def makeDefaultUsersResponderV1: UsersResponderV1 = new UsersResponderV1(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default users V1 responder.
    */
  protected lazy val usersResponderV1: UsersResponderV1 = makeDefaultUsersResponderV1

  /**
    * Constructs the default Akka routing actor that routes messages to [[ListsResponderV1]].
    */
  protected final def makeDefaultListsResponderV1: ListsResponderV1 = new ListsResponderV1(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default lists V1 responder.
    */
  protected lazy val listsResponderV1: ListsResponderV1 = makeDefaultListsResponderV1

  /**
    * Constructs the default Akka routing actor that routes messages to [[SearchResponderV1]].
    */
  protected final def makeDefaultSearchResponderV1: SearchResponderV1 = new SearchResponderV1(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default search V1 responder.
    */
  protected lazy val searchResponderV1: SearchResponderV1 = makeDefaultSearchResponderV1

  /**
    * Constructs the default Akka routing actor that routes messages to [[OntologyResponderV1]].
    */
  protected final def makeDefaultOntologyResponderV1: OntologyResponderV1 = new OntologyResponderV1(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default ontology V1 responder.
    */
  protected lazy val ontologyResponderV1: OntologyResponderV1 = makeDefaultOntologyResponderV1

  /**
    * Constructs the default Akka routing actor that routes messages to [[ProjectsResponderV1]].
    */
  protected final def makeDefaultProjectsResponderV1: ProjectsResponderV1 = new ProjectsResponderV1(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default projects V1 responder.
    */
  protected lazy val projectsResponderV1: ProjectsResponderV1 = makeDefaultProjectsResponderV1

  //
  // V2 responders
  //

  /**
    * Constructs the default [[OntologyResponderV2]].
    */
  protected final def makeDefaultOntologiesResponderV2: OntologyResponderV2 = new OntologyResponderV2(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default ontologies responder.
    */
  protected val ontologiesResponderV2: OntologyResponderV2 = makeDefaultOntologiesResponderV2

  /**
    * Constructs the default [[SearchResponderV2]].
    */
  protected final def makeDefaultSearchResponderV2: SearchResponderV2 = new SearchResponderV2(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default search responder.
    */
  protected val searchResponderV2: SearchResponderV2 = makeDefaultSearchResponderV2

  /**
    * Constructs the default [[ResourcesResponderV2]].
    */
  protected final def makeDefaultResourcesResponderV2: ResourcesResponderV2 =
    ResourcesResponderFeatureFactoryV2.makeResourcesResponderV2(responderData = responderData,
                                                                featureFactoryConfig = featureFactoryConfig)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default resources responder.
    */
  protected val resourcesResponderV2: ResourcesResponderV2 = makeDefaultResourcesResponderV2

  /**
    * Constructs the default [[ValuesResponderV2]].
    */
  protected final def makeDefaultValuesResponderV2: ValuesResponderV2 = new ValuesResponderV2(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default values responder.
    */
  protected val valuesResponderV2: ValuesResponderV2 = makeDefaultValuesResponderV2

  /**
    * Constructs the default [[StandoffResponderV2]].
    */
  protected final def makeDefaultStandoffResponderV2: StandoffResponderV2 = new StandoffResponderV2(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default standoff responder.
    */
  protected val standoffResponderV2: StandoffResponderV2 = makeDefaultStandoffResponderV2

  /**
    * Constructs the default [[ListsResponderV2]].
    */
  protected final def makeDefaultListsResponderV2: ListsResponderV2 = new ListsResponderV2(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default lists responder.
    */
  protected val listsResponderV2: ListsResponderV2 = makeDefaultListsResponderV2

  /**
    * Constructs the default [[MetadataResponderV2]].
    */
  protected final def makeDefaultMetadataResponderV2: MetadataResponderV2 = new MetadataResponderV2(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default metadata responder.
    */
  protected val metadataResponderV2: MetadataResponderV2 = makeDefaultMetadataResponderV2

  //
  // Admin responders
  //

  /**
    * Constructs the default [[GroupsResponderADM]].
    */
  protected final def makeDefaultGroupsResponderADM: GroupsResponderADM = new GroupsResponderADM(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default groups responder.
    */
  protected val groupsResponderADM: GroupsResponderADM = makeDefaultGroupsResponderADM

  /**
    * Constructs the default [[ListsResponderADM]].
    */
  protected final def makeDefaultListsResponderADM: ListsResponderADM = new ListsResponderADM(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default admin lists responder.
    */
  protected val listsResponderADM: ListsResponderADM = makeDefaultListsResponderADM

  /**
    * Constructs the default [[PermissionsResponderADM]].
    */
  protected final def makeDefaultPermissionsResponderADM: PermissionsResponderADM =
    new PermissionsResponderADM(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default permissions responder.
    */
  protected val permissionsResponderADM: PermissionsResponderADM = makeDefaultPermissionsResponderADM

  /**
    * Constructs the default [[ProjectsResponderADM]].
    */
  protected final def makeDefaultProjectsResponderADM: ProjectsResponderADM = new ProjectsResponderADM(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default projects responder.
    */
  protected val projectsResponderADM: ProjectsResponderADM = makeDefaultProjectsResponderADM

  /**
    * Constructs the default [[StoresResponderADM]].
    */
  protected final def makeDefaultStoreResponderADM: StoresResponderADM = new StoresResponderADM(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default store responder.
    */
  protected val storeResponderADM: StoresResponderADM = makeDefaultStoreResponderADM

  /**
    * Constructs the default [[UsersResponderADM]].
    */
  protected final def makeDefaultUsersResponderADM: UsersResponderADM = new UsersResponderADM(responderData)

  /**
    * Subclasses can override this member to substitute a custom implementation instead of the default users responder.
    */
  protected val usersResponderADM: UsersResponderADM = makeDefaultUsersResponderADM

  /**
    * Constructs the default [[SipiResponderADM]].
    */
  protected final def makeDefaultSipiResponderADM: SipiResponderADM = new SipiResponderADM(responderData)

  /**
    * Subclasses can override this member to substitute it with a custom implementation instead of the default sipi responder.
    */
  protected lazy val sipiRouterADM: SipiResponderADM = makeDefaultSipiResponderADM

  /**
    * Each responder's receive method is called and only messages of the allowed type are supplied as the parameter.
    * If a serious error occurs (i.e. an error that isn't the client's fault), the future2Message method first
    * returns `Failure` to the sender, then throws an exception.
    */
  def receive: Receive = LoggingReceive {
    // Knora API V1 messages
    case ckanResponderRequestV1: CkanResponderRequestV1 =>
      future2Message(sender(), ckanResponderV1 receive ckanResponderRequestV1, log)
    case resourcesResponderRequestV1: ResourcesResponderRequestV1 =>
      future2Message(sender(), resourcesResponderV1 receive resourcesResponderRequestV1, log)
    case valuesResponderRequestV1: ValuesResponderRequestV1 =>
      future2Message(sender(), valuesResponderV1 receive valuesResponderRequestV1, log)
    case listsResponderRequestV1: ListsResponderRequestV1 =>
      future2Message(sender(), listsResponderV1 receive listsResponderRequestV1, log)
    case searchResponderRequestV1: SearchResponderRequestV1 =>
      future2Message(sender(), searchResponderV1 receive searchResponderRequestV1, log)
    case ontologyResponderRequestV1: OntologyResponderRequestV1 =>
      future2Message(sender(), ontologyResponderV1 receive ontologyResponderRequestV1, log)
    case standoffResponderRequestV1: StandoffResponderRequestV1 =>
      future2Message(sender(), standoffResponderV1 receive standoffResponderRequestV1, log)
    case usersResponderRequestV1: UsersResponderRequestV1 =>
      future2Message(sender(), usersResponderV1 receive usersResponderRequestV1, log)
    case projectsResponderRequestV1: ProjectsResponderRequestV1 =>
      future2Message(sender(), projectsResponderV1 receive projectsResponderRequestV1, log)

    // Knora API V2 messages
    case ontologiesResponderRequestV2: OntologiesResponderRequestV2 =>
      future2Message(sender(), ontologiesResponderV2 receive ontologiesResponderRequestV2, log)
    case searchResponderRequestV2: SearchResponderRequestV2 =>
      future2Message(sender(), searchResponderV2 receive searchResponderRequestV2, log)
    case resourcesResponderRequestV2: ResourcesResponderRequestV2 =>
      future2Message(sender(), resourcesResponderV2 receive resourcesResponderRequestV2, log)
    case valuesResponderRequestV2: ValuesResponderRequestV2 =>
      future2Message(sender(), valuesResponderV2 receive valuesResponderRequestV2, log)
    case standoffResponderRequestV2: StandoffResponderRequestV2 =>
      future2Message(sender(), standoffResponderV2 receive standoffResponderRequestV2, log)
    case listsResponderRequestV2: ListsResponderRequestV2 =>
      future2Message(sender(), listsResponderV2 receive listsResponderRequestV2, log)
    case metadataResponderRequestV2: MetadataResponderRequestV2 =>
      future2Message(sender(), metadataResponderV2 receive metadataResponderRequestV2, log)

    // Knora Admin message
    case groupsResponderRequestADM: GroupsResponderRequestADM =>
      future2Message(sender(), groupsResponderADM receive groupsResponderRequestADM, log)
    case listsResponderRequest: ListsResponderRequestADM =>
      future2Message(sender(), listsResponderADM receive listsResponderRequest, log)
    case permissionsResponderRequestADM: PermissionsResponderRequestADM =>
      future2Message(sender(), permissionsResponderADM receive permissionsResponderRequestADM, log)
    case projectsResponderRequestADM: ProjectsResponderRequestADM =>
      future2Message(sender(), projectsResponderADM receive projectsResponderRequestADM, log)
    case storeResponderRequestADM: StoreResponderRequestADM =>
      future2Message(sender(), storeResponderADM receive storeResponderRequestADM, log)
    case usersResponderRequestADM: UsersResponderRequestADM =>
      future2Message(sender(), usersResponderADM receive usersResponderRequestADM, log)
    case sipiResponderRequestADM: SipiResponderRequestADM =>
      future2Message(sender(), sipiRouterADM receive sipiResponderRequestADM, log)

    case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
  }
}
