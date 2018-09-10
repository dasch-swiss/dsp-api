/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive
import akka.routing.FromConfig
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsResponderRequestADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListsResponderRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsResponderRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsResponderRequestADM
import org.knora.webapi.messages.admin.responder.storesmessages.StoreResponderRequestADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersResponderRequestADM
import org.knora.webapi.messages.v1.responder.ckanmessages.CkanResponderRequestV1
import org.knora.webapi.messages.v1.responder.listmessages.ListsResponderRequestV1
import org.knora.webapi.messages.v1.responder.ontologymessages.OntologyResponderRequestV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectsResponderRequestV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourcesResponderRequestV1
import org.knora.webapi.messages.v1.responder.searchmessages.SearchResponderRequestV1
import org.knora.webapi.messages.v1.responder.sipimessages.SipiResponderRequestV1
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffResponderRequestV1
import org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1
import org.knora.webapi.messages.v1.responder.valuemessages.ValuesResponderRequestV1
import org.knora.webapi.messages.v2.responder.listsmessages.ListsResponderRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologiesResponderRequestV2
import org.knora.webapi.messages.v2.responder.persistentmapmessages.PersistentMapResponderRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesResponderRequestV2
import org.knora.webapi.messages.v2.responder.searchmessages.SearchResponderRequestV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffResponderRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.ValuesResponderRequestV2
import org.knora.webapi.responders.admin._
import org.knora.webapi.responders.v1._
import org.knora.webapi.responders.v2._
import org.knora.webapi.util.ActorUtil.handleUnexpectedMessage
import org.knora.webapi.{ActorMaker, KnoraDispatchers}

import scala.concurrent.ExecutionContext

/**
  * This actor receives messages representing client requests, and forwards them to pools specialised actors that it supervises.
  */
class ResponderManager extends Actor with ActorLogging {
    this: ActorMaker =>

    /**
      * The responder's Akka actor system.
      */
    protected implicit val system: ActorSystem = context.system

    /**
      * The Akka actor system's execution context for futures.
      */
    protected implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    // A subclass can replace the standard responders with custom responders, e.g. for testing. To do this, it must
    // override one or more of the protected val members below representing actors that route requests to particular
    // responder classes. To construct a default responder router, a subclass can call one of the protected methods below.

    /**
      * Constructs the default Akka routing actor that routes messages to [[ResourcesResponderV1]].
      */
    protected final def makeDefaultResourcesRouterV1: ActorRef = makeActor(Props[ResourcesResponderV1].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), RESOURCES_V1_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the resources responder. Subclasses can override this
      * member to substitute a custom actor instead of the default resources responder.
      */
    protected lazy val resourcesRouterV1: ActorRef = makeDefaultResourcesRouterV1

    /**
      * Constructs the default Akka routing actor that routes messages to [[ValuesResponderV1]].
      */
    protected final def makeDefaultValuesRouterV1: ActorRef = makeActor(Props[ValuesResponderV1].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), VALUES_V1_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the values responder. Subclasses can override this
      * member to substitute a custom actor instead of the default values responder.
      */
    protected lazy val valuesRouterV1: ActorRef = makeDefaultValuesRouterV1

    /**
      * Constructs the [[SipiResponderV1]] actor pool.
      */
    protected final def makeDefaultSipiRouterV1: ActorRef = makeActor(FromConfig.props(Props[SipiResponderV1]).withDispatcher(KnoraDispatchers.KnoraActorDispatcher), SIPI_ROUTER_V1_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the Sipi responder. Subclasses can override this
      * member to substitute a custom actor instead of the default Sipi responder.
      */
    protected lazy val sipiRouterV1: ActorRef = makeDefaultSipiRouterV1

    /**
      * Constructs the default Akka routing actor that routes messages to [[StandoffResponderV1]].
      */
    protected final def makeDefaultStandoffRouterV1: ActorRef = makeActor(Props[StandoffResponderV1].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), STANDOFF_V1_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the Sipi responder. Subclasses can override this
      * member to substitute a custom actor instead of the default Sipi responder.
      */
    protected lazy val standoffRouterV1: ActorRef = makeDefaultStandoffRouterV1

    /**
      * Constructs the default Akka routing actor that routes messages to [[UsersResponderV1]].
      */
    protected final def makeDefaultUsersRouterV1: ActorRef = makeActor(Props[UsersResponderV1].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), USERS_V1_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the users responder. Subclasses can override this
      * member to substitute a custom actor instead of the default users responder.
      */
    protected lazy val usersRouterV1: ActorRef = makeDefaultUsersRouterV1

    /**
      * Constructs the default Akka routing actor that routes messages to [[ListsResponderV1]].
      */
    protected final def makeDefaultListsRouterV1: ActorRef = makeActor(Props[ListsResponderV1].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), LISTS_V1_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the lists responder. Subclasses can override this
      * member to substitute a custom actor instead of the default lists responder.
      */
    protected lazy val listsRouterV1: ActorRef = makeDefaultListsRouterV1

    /**
      * Constructs the default Akka routing actor that routes messages to [[SearchResponderV1]].
      */
    protected final def makeDefaultSearchRouterV1: ActorRef = makeActor(Props[SearchResponderV1].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), SEARCH_V1_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the search responder. Subclasses can override this
      * member to substitute a custom actor instead of the default search responder.
      */
    protected lazy val searchRouterV1: ActorRef = makeDefaultSearchRouterV1

    /**
      * Constructs the default Akka routing actor that routes messages to [[OntologyResponderV1]].
      */
    protected final def makeDefaultOntologyRouterV1: ActorRef = makeActor(Props[OntologyResponderV1].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), ONTOLOGY_V1_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the ontology responder. Subclasses can override this
      * member to substitute a custom actor instead of the default ontology responder.
      */
    protected lazy val ontologyRouterV1: ActorRef = makeDefaultOntologyRouterV1

    /**
      * Constructs the default Akka routing actor that routes messages to [[ProjectsResponderV1]].
      */
    protected final def makeDefaultProjectsRouterV1: ActorRef = makeActor(Props[ProjectsResponderV1].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), PROJECTS_V1_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the projects responder. Subclasses can override this
      * member to substitute a custom actor instead of the default projects responder.
      */
    protected lazy val projectsRouterV1: ActorRef = makeDefaultProjectsRouterV1

    /**
      * Constructs the default Akka routing actor that routes messages to [[CkanResponderV1]].
      */
    protected final def makeDefaultCkanRouterV1: ActorRef = makeActor(Props[CkanResponderV1].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), CKAN_V1_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the Ckan responder. Subclasses can override this
      * member to substitute a custom actor instead of the default Ckan responder.
      */
    protected lazy val ckanRouterV1: ActorRef = makeDefaultCkanRouterV1


    //
    // V2 responders
    //

    /**
      * Constructs the default Akka routing actor that routes messages to [[OntologyResponderV2]].
      */
    protected final def makeDefaultOntologiesRouterV2: ActorRef = makeActor(Props[OntologyResponderV2].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), ONTOLOGY_V2_ACTOR_NAME)

    /**
      * Constructs the default Akka routing actor that routes messages to [[SearchResponderV2]].
      */
    protected final def makeDefaultSearchRouterV2: ActorRef = makeActor(Props[SearchResponderV2].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), SEARCH_V2_ACTOR_NAME)

    /**
      * Constructs the default Akka routing actor that routes messages to [[ResourcesResponderV2]].
      */
    protected final def makeDefaultResourcesRouterV2: ActorRef = makeActor(Props[ResourcesResponderV2].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), RESOURCES_V2_ACTOR_NAME)

    /**
      * Constructs the default Akka routing actor that routes messages to [[ValuesResponderV2]].
      */
    protected final def makeDefaultValuesRouterV2: ActorRef = makeActor(Props[ValuesResponderV2].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), VALUES_V2_ACTOR_NAME)

    /**
      * Constructs the default Akka routing actor that routes messages to [[PersistentMapResponderV2]].
      */
    protected final def makeDefaultPersistentMapRouterV2: ActorRef = makeActor(Props[PersistentMapResponderV2].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), PERSISTENT_MAP_V2_ACTOR_NAME)

    /**
      * Constructs the default Akka routing actor that routes messages to [[StandoffResponderV2]].
      */
    protected final def makeDefaultStandoffRouterV2: ActorRef = makeActor(Props[StandoffResponderV2].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), STANDOFF_V2_ACTOR_NAME)

    /**
      * Constructs the default Akka routing actor that routes messages to [[ListsResponderV2]].
      */
    protected final def makeDefaultListsRouterV2: ActorRef = makeActor(Props[ListsResponderV2].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), LISTS_V2_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the ontology responder. Subclasses can override this
      * member to substitute a custom actor instead of the default ontology responder.
      */
    protected val ontologiesRouterV2: ActorRef = makeDefaultOntologiesRouterV2

    /**
      * The Akka routing actor that should receive messages addressed to the search responder. Subclasses can override this
      * member to substitute a custom actor instead of the default search responder.
      */
    protected val searchRouterV2: ActorRef = makeDefaultSearchRouterV2

    /**
      * The Akka routing actor that should receive messages addressed to the resources responder. Subclasses can override this
      * member to substitute a custom actor instead of the default resources responder.
      */
    protected val resourcesRouterV2: ActorRef = makeDefaultResourcesRouterV2

    /**
      * The Akka routing actor that should receive messages addressed to the resources responder. Subclasses can override this
      * member to substitute a custom actor instead of the default resources responder.
      */
    protected val valuesRouterV2: ActorRef = makeDefaultValuesRouterV2

    /**
      * The Akka routing actor that should receive messages addressed to the persistent map responder. Subclasses can override this
      * member to substitute a custom actor instead of the default persistent map responder.
      */
    protected val persistentMapRouterV2: ActorRef = makeDefaultPersistentMapRouterV2

    /**
      * The Akka routing actor that should receive messages addressed to the resources responder. Subclasses can override this
      * member to substitute a custom actor instead of the default resources responder.
      */
    protected val standoffRouterV2: ActorRef = makeDefaultStandoffRouterV2

    /**
      * The Akka routing actor that should receive messages addressed to the resources responder. Subclasses can override this
      * member to substitute a custom actor instead of the default resources responder.
      */
    protected val listsRouterV2: ActorRef = makeDefaultListsRouterV2

    //
    // Admin responders
    //

    /**
      * Constructs the default Akka routing actor that routes messages to [[GroupsResponderADM]].
      */
    protected final def makeDefaultGroupsRouterADM: ActorRef = makeActor(Props[GroupsResponderADM].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), GROUPS_ADM_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the groups responder. Subclasses can override this
      * member to substitute a custom actor instead of the default groups responder.
      */
    protected val groupsRouterADM: ActorRef = makeDefaultGroupsRouterADM

    /**
      * Constructs the default Akka routing actor that routes messages to [[ListsResponderADM]].
      */
    protected final def makeDefaultListsAdminRouter: ActorRef = makeActor(Props[ListsResponderADM].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), LISTS_ADM_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the lists responder. Subclasses can override this
      * member to substitute a custom actor instead of the default lists responder.
      */
    protected val listsAdminRouter: ActorRef = makeDefaultListsAdminRouter

    /**
      * Constructs the default Akka routing actor that routes messages to [[PermissionsResponderADM]].
      */
    protected final def makeDefaultPermissionsRouterADM: ActorRef = makeActor(Props[PermissionsResponderADM].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), PERMISSIONS_ADM_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the Permissions responder. Subclasses can override this
      * member to substitute a custom actor instead of the default Permissions responder.
      */
    protected val permissionsRouterADM: ActorRef = makeDefaultPermissionsRouterADM

    /**
      * Constructs the default Akka routing actor that routes messages to [[ProjectsResponderADM]].
      */
    protected final def makeDefaultProjectsRouterADM: ActorRef = makeActor(Props[ProjectsResponderADM].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), PROJECTS_ADM_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the projects responder. Subclasses can override this
      * member to substitute a custom actor instead of the default projects responder.
      */
    protected val projectsRouterADM: ActorRef = makeDefaultProjectsRouterADM

    /**
      * Constructs the default Akka routing actor that routes messages to [[StoresResponderADM]].
      */
    protected final def makeDefaultStoreRouterADM: ActorRef = makeActor(Props[StoresResponderADM].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), STORE_ADM_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the Store responder. Subclasses can override this
      * member to substitute a custom actor instead of the default Store responder.
      */
    protected val storeRouterADM: ActorRef = makeDefaultStoreRouterADM

    /**
      * Constructs the default Akka routing actor that routes messages to [[UsersResponderADM]].
      */
    protected final def makeDefaultUsersRouterADM: ActorRef = makeActor(Props[UsersResponderADM].withDispatcher(KnoraDispatchers.KnoraActorDispatcher), USERS_ADM_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the users responder. Subclasses can override this
      * member to substitute a custom actor instead of the default users responder.
      */
    protected val usersRouterADM: ActorRef = makeDefaultUsersRouterADM


    def receive = LoggingReceive {
        // Knora API V1 messages
        case resourcesResponderRequestV1: ResourcesResponderRequestV1 => resourcesRouterV1.forward(resourcesResponderRequestV1)
        case valuesResponderRequestV1: ValuesResponderRequestV1 => valuesRouterV1.forward(valuesResponderRequestV1)
        case sipiResponderRequestV1: SipiResponderRequestV1 => sipiRouterV1.forward(sipiResponderRequestV1)
        case listsResponderRequestV1: ListsResponderRequestV1 => listsRouterV1.forward(listsResponderRequestV1)
        case searchResponderRequestV1: SearchResponderRequestV1 => searchRouterV1.forward(searchResponderRequestV1)
        case ontologyResponderRequestV1: OntologyResponderRequestV1 => ontologyRouterV1.forward(ontologyResponderRequestV1)
        case ckanResponderRequestV1: CkanResponderRequestV1 => ckanRouterV1.forward(ckanResponderRequestV1)
        case standoffResponderRequestV1: StandoffResponderRequestV1 => standoffRouterV1.forward(standoffResponderRequestV1)
        case usersResponderRequestV1: UsersResponderRequestV1 => usersRouterV1.forward(usersResponderRequestV1)
        case projectsResponderRequestV1: ProjectsResponderRequestV1 => projectsRouterV1.forward(projectsResponderRequestV1)

        // Knora API V2 messages
        case ontologiesResponderRequestV2: OntologiesResponderRequestV2 => ontologiesRouterV2.forward(ontologiesResponderRequestV2)
        case searchResponderRequestV2: SearchResponderRequestV2 => searchRouterV2.forward(searchResponderRequestV2)
        case resourcesResponderRequestV2: ResourcesResponderRequestV2 => resourcesRouterV2.forward(resourcesResponderRequestV2)
        case valuesResponderRequestV2: ValuesResponderRequestV2 => valuesRouterV2.forward(valuesResponderRequestV2)
        case persistentMapResponderRequestV2: PersistentMapResponderRequestV2 => persistentMapRouterV2.forward(persistentMapResponderRequestV2)
        case standoffResponderRequestV2: StandoffResponderRequestV2 => standoffRouterV2.forward(standoffResponderRequestV2)
        case listsResponderRequestV2: ListsResponderRequestV2 => listsRouterV2.forward(listsResponderRequestV2)

        // Knora Admin message
        case groupsResponderRequestADM: GroupsResponderRequestADM => groupsRouterADM.forward(groupsResponderRequestADM)
        case listsResponderRequest: ListsResponderRequestADM => listsAdminRouter forward listsResponderRequest
        case permissionsResponderRequestADM: PermissionsResponderRequestADM => permissionsRouterADM.forward(permissionsResponderRequestADM)
        case projectsResponderRequestADM: ProjectsResponderRequestADM => projectsRouterADM.forward(projectsResponderRequestADM)
        case storeResponderRequestADM: StoreResponderRequestADM => storeRouterADM.forward(storeResponderRequestADM)
        case usersResponderRequestADM: UsersResponderRequestADM => usersRouterADM.forward(usersResponderRequestADM)

        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }
}
