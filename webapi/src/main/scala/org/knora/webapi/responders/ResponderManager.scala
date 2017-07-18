/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import akka.routing.FromConfig
import org.knora.webapi.ActorMaker
import org.knora.webapi.messages.v1.responder.ckanmessages.CkanResponderRequestV1
import org.knora.webapi.messages.v1.responder.groupmessages.GroupsResponderRequestV1
import org.knora.webapi.messages.v1.responder.listmessages.ListsResponderRequestV1
import org.knora.webapi.messages.v1.responder.ontologymessages.OntologyResponderRequestV1
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionsResponderRequestV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectsResponderRequestV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourcesResponderRequestV1
import org.knora.webapi.messages.v1.responder.searchmessages.SearchResponderRequestV1
import org.knora.webapi.messages.v1.responder.sipimessages.SipiResponderRequestV1
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffResponderRequestV1
import org.knora.webapi.messages.v1.responder.storemessages.StoreResponderRequestV1
import org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1
import org.knora.webapi.messages.v1.responder.valuemessages.ValuesResponderRequestV1
import org.knora.webapi.messages.v2.responder.ontologymessages.OntologiesResponderRequestV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ResourcesResponderRequestV2
import org.knora.webapi.messages.v2.responder.searchmessages.SearchResponderRequestV2
import org.knora.webapi.responders.v1._
import org.knora.webapi.responders.v2.{OntologiesResponderV2, ResourcesResponderV2, SearchResponderV2}
import org.knora.webapi.util.ActorUtil.handleUnexpectedMessage

/**
  * This actor receives messages representing client requests, and forwards them to pools specialised actors that it supervises.
  */
class ResponderManager extends Actor with ActorLogging {
    this: ActorMaker =>

    /**
      * The responder's Akka actor system.
      */
    protected implicit val system = context.system

    /**
      * The Akka actor system's execution context for futures.
      */
    protected implicit val executionContext = system.dispatcher

    // A subclass can replace the standard responders with custom responders, e.g. for testing. To do this, it must
    // override one or more of the protected val members below representing actors that route requests to particular
    // responder classes. To construct a default responder router, a subclass can call one of the protected methods below.

    /**
      * Constructs the default Akka routing actor that routes messages to [[ResourcesResponderV1]].
      */
    protected final def makeDefaultResourcesRouter = makeActor(FromConfig.props(Props[ResourcesResponderV1]), RESOURCES_ROUTER_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the resources responder. Subclasses can override this
      * member to substitute a custom actor instead of the default resources responder.
      */
    protected val resourcesRouter = makeDefaultResourcesRouter

    /**
      * Constructs the default Akka routing actor that routes messages to [[ValuesResponderV1]].
      */
    protected final def makeDefaultValuesRouter = makeActor(FromConfig.props(Props[ValuesResponderV1]), VALUES_ROUTER_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the values responder. Subclasses can override this
      * member to substitute a custom actor instead of the default values responder.
      */
    protected val valuesRouter = makeDefaultValuesRouter

    /**
      * Constructs the default Akka routing actor that routes messages to [[SipiResponderV1]].
      */
    protected final def makeDefaultSipiRouter = makeActor(FromConfig.props(Props[SipiResponderV1]), SIPI_ROUTER_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the Sipi responder. Subclasses can override this
      * member to substitute a custom actor instead of the default Sipi responder.
      */
    protected val sipiRouter = makeDefaultSipiRouter

    /**
      * Constructs the default Akka routing actor that routes messages to [[StandoffResponderV1]].
      */
    protected final def makeDefaultStandoffRouter = makeActor(FromConfig.props(Props[StandoffResponderV1]), STANDOFF_ROUTER_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the Sipi responder. Subclasses can override this
      * member to substitute a custom actor instead of the default Sipi responder.
      */
    protected val standoffRouter = makeDefaultStandoffRouter

    /**
      * Constructs the default Akka routing actor that routes messages to [[UsersResponderV1]].
      */
    protected final def makeDefaultUsersRouter = makeActor(FromConfig.props(Props[UsersResponderV1]), USERS_ROUTER_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the users responder. Subclasses can override this
      * member to substitute a custom actor instead of the default users responder.
      */
    protected val usersRouter = makeDefaultUsersRouter

    /**
      * Constructs the default Akka routing actor that routes messages to [[ListsResponderV1]].
      */
    protected final def makeDefaultListsRouter = makeActor(FromConfig.props(Props[ListsResponderV1]), LISTS_ROUTER_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the lists responder. Subclasses can override this
      * member to substitute a custom actor instead of the default lists responder.
      */
    protected val listsRouter = makeDefaultListsRouter

    /**
      * Constructs the default Akka routing actor that routes messages to [[SearchResponderV1]].
      */
    protected final def makeDefaultSearchRouter = makeActor(FromConfig.props(Props[SearchResponderV1]), SEARCH_ROUTER_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the search responder. Subclasses can override this
      * member to substitute a custom actor instead of the default search responder.
      */
    protected val searchRouter = makeDefaultSearchRouter

    /**
      * Constructs the default Akka routing actor that routes messages to [[OntologyResponderV1]].
      */
    protected final def makeDefaultOntologyRouter = makeActor(FromConfig.props(Props[OntologyResponderV1]), ONTOLOGY_ROUTER_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the ontology responder. Subclasses can override this
      * member to substitute a custom actor instead of the default ontology responder.
      */
    protected val ontologyRouter = makeDefaultOntologyRouter

    /**
      * Constructs the default Akka routing actor that routes messages to [[ProjectsResponderV1]].
      */
    protected final def makeDefaultProjectsRouter = makeActor(FromConfig.props(Props[ProjectsResponderV1]), PROJECTS_ROUTER_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the projects responder. Subclasses can override this
      * member to substitute a custom actor instead of the default projects responder.
      */
    protected val projectsRouter = makeDefaultProjectsRouter

    /**
      * Constructs the default Akka routing actor that routes messages to [[CkanResponderV1]].
      */
    protected final def makeDefaultCkanRouter = makeActor(FromConfig.props(Props[CkanResponderV1]), CKAN_ROUTER_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the Ckan responder. Subclasses can override this
      * member to substitute a custom actor instead of the default Ckan responder.
      */
    protected val ckanRouter = makeDefaultCkanRouter

    /**
      * Constructs the default Akka routing actor that routes messages to [[StoreResponderV1]].
      */
    protected final def makeDefaultStoreRouter = makeActor(FromConfig.props(Props[StoreResponderV1]), STORE_ROUTER_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the Store responder. Subclasses can override this
      * member to substitute a custom actor instead of the default Store responder.
      */
    protected val storeRouter = makeDefaultStoreRouter

    /**
      * Constructs the default Akka routing actor that routes messages to [[PermissionsResponderV1]].
      */
    protected final def makeDefaultPermissionsRouter = makeActor(FromConfig.props(Props[PermissionsResponderV1]), PERMISSIONS_ROUTER_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the Permissions responder. Subclasses can override this
      * member to substitute a custom actor instead of the default Store responder.
      */
    protected val permissionsRouter = makeDefaultPermissionsRouter

    /**
      * Constructs the default Akka routing actor that routes messages to [[GroupsResponderV1]].
      */
    protected final def makeDefaultGroupsRouter = makeActor(FromConfig.props(Props[GroupsResponderV1]), GROUPS_ROUTER_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the Permissions responder. Subclasses can override this
      * member to substitute a custom actor instead of the default Store responder.
      */
    protected val groupsRouter = makeDefaultGroupsRouter

    //
    // V2 responders
    //

    /**
      * Constructs the default Akka routing actor that routes messages to [[OntologiesResponderV2]].
      */
    protected final def makeDefaultOntologiesRouter2 = makeActor(FromConfig.props(Props[OntologiesResponderV2]), ONTOLOGIES_ROUTER_ACTOR_NAME2)

    /**
      * Constructs the default Akka routing actor that routes messages to [[SearchResponderV2]].
      */
    protected final def makeDefaultSearchRouter2 = makeActor(FromConfig.props(Props[SearchResponderV2]), SEARCH_ROUTER_ACTOR_NAME2)

    /**
      * Constructs the default Akka routing actor that routes messages to [[ResourcesResponderV2]].
      */
    protected final def makeDefaultResourcesRouter2 = makeActor(FromConfig.props(Props[ResourcesResponderV2]), RESOURCES_ROUTER_ACTOR_NAME2)

    /**
      * The Akka routing actor that should receive messages addressed to the ontologies responder. Subclasses can override this
      * member to substitute a custom actor instead of the default search responder.
      */
    protected val ontologiesRouter2 = makeDefaultOntologiesRouter2

    /**
      * The Akka routing actor that should receive messages addressed to the search responder. Subclasses can override this
      * member to substitute a custom actor instead of the default search responder.
      */
    protected val searchRouter2 = makeDefaultSearchRouter2

    /**
      * The Akka routing actor that should receive messages addressed to the resources responder. Subclasses can override this
      * member to substitute a custom actor instead of the default search responder.
      */
    protected val resourcesRouter2 = makeDefaultResourcesRouter2

    def receive = LoggingReceive {
        case resourcesResponderRequestV1: ResourcesResponderRequestV1 => resourcesRouter.forward(resourcesResponderRequestV1)
        case valuesResponderRequest: ValuesResponderRequestV1 => valuesRouter.forward(valuesResponderRequest)
        case sipiResponderRequest: SipiResponderRequestV1 => sipiRouter.forward(sipiResponderRequest)
        case listsResponderRequest: ListsResponderRequestV1 => listsRouter.forward(listsResponderRequest)
        case searchResponderRequest: SearchResponderRequestV1 => searchRouter.forward(searchResponderRequest)
        case ontologyResponderRequest: OntologyResponderRequestV1 => ontologyRouter.forward(ontologyResponderRequest)
        case ckanResponderRequest: CkanResponderRequestV1 => ckanRouter.forward(ckanResponderRequest)
        case storeResponderRequest: StoreResponderRequestV1 => storeRouter.forward(storeResponderRequest)
        case standoffResponderRequest: StandoffResponderRequestV1 => standoffRouter forward standoffResponderRequest
		case permissionsResponderRequest: PermissionsResponderRequestV1 => permissionsRouter forward permissionsResponderRequest
        case usersResponderRequest: UsersResponderRequestV1 => usersRouter forward usersResponderRequest
        case projectsResponderRequest: ProjectsResponderRequestV1 => projectsRouter forward projectsResponderRequest
        case groupsResponderRequest: GroupsResponderRequestV1 => groupsRouter forward groupsResponderRequest
        case ontologiesResponderRequest: OntologiesResponderRequestV2 => ontologiesRouter2.forward(ontologiesResponderRequest) // V2
        case searchResponderRequest: SearchResponderRequestV2 => searchRouter2.forward(searchResponderRequest) // V2
        case resourcesResponderRequest: ResourcesResponderRequestV2 => resourcesRouter2.forward(resourcesResponderRequest) // V2
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }
}
