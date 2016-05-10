/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.responders.v1

import akka.actor.{Actor, ActorLogging, Props, Status}
import akka.event.LoggingReceive
import akka.routing.FromConfig
import org.knora.webapi.messages.v1respondermessages.ckanmessages.CkanResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.graphdatamessages.GraphDataResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.listmessages.ListsResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.ontologymessages.OntologyResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.projectmessages.ProjectsResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.resourcemessages.ResourcesResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.searchmessages.SearchResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.sipimessages.SipiResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.TriplestoreRequest
import org.knora.webapi.messages.v1respondermessages.usermessages.UsersResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.valuemessages.ValuesResponderRequestV1
import org.knora.webapi.responders._
import org.knora.webapi.{ActorMaker, UnexpectedMessageException}

/**
  * This actor receives messages representing client requests, and forwards them to pools specialised actors that it supervises.
  */
class ResponderManagerV1 extends Actor with ActorLogging {
    this: ActorMaker =>

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
      * Constructs the default Akka routing actor that routes messages to [[UsersResponderV1]].
      */
    protected final def makeDefaultUsersRouter = makeActor(FromConfig.props(Props[UsersResponderV1]), USERS_ROUTER_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the users responder. Subclasses can override this
      * member to substitute a custom actor instead of the default users responder.
      */
    protected val usersRouter = makeDefaultUsersRouter

    /**
      * Constructs the default Akka routing actor that routes messages to [[HierarchicalListsResponderV1]].
      */
    protected final def makeDefaultListsRouter = makeActor(FromConfig.props(Props[HierarchicalListsResponderV1]), HIERARCHICAL_LISTS_ROUTER_ACTOR_NAME)

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

    def receive = LoggingReceive {
        case resourcesResponderRequestV1: ResourcesResponderRequestV1 => resourcesRouter.forward(resourcesResponderRequestV1)
        case valuesResponderRequest: ValuesResponderRequestV1 => valuesRouter.forward(valuesResponderRequest)
        case sipiResponderRequest: SipiResponderRequestV1 => sipiRouter.forward(sipiResponderRequest)
        case usersResponderRequest: UsersResponderRequestV1 => usersRouter forward usersResponderRequest
        case listsResponderRequest: ListsResponderRequestV1 => listsRouter.forward(listsResponderRequest)
        case searchResponderRequest: SearchResponderRequestV1 => searchRouter.forward(searchResponderRequest)
        case ontologyResponderRequest: OntologyResponderRequestV1 => ontologyRouter.forward(ontologyResponderRequest)
        case graphdataResponderRequest: GraphDataResponderRequestV1 => resourcesRouter.forward(graphdataResponderRequest)
        case projectsResponderRequest: ProjectsResponderRequestV1 => projectsRouter forward projectsResponderRequest
        case ckanResponderRequest: CkanResponderRequestV1 => ckanRouter forward ckanResponderRequest
        case triplestoreManagerRequest: TriplestoreRequest =>
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }
}
