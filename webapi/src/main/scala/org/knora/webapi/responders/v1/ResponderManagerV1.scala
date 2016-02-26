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
import org.knora.webapi.messages.v1respondermessages.usermessages.UsersResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.valuemessages.ValuesResponderRequestV1
import org.knora.webapi.responders._
import org.knora.webapi.{ActorMaker, UnexpectedMessageException}

/**
  * This actor receives messages representing client requests, and forwards them to pools specialised actors that it supervises.
  */
class ResponderManagerV1 extends Actor with ActorLogging {
    this: ActorMaker =>

    protected val resourcesRouter = makeActor(FromConfig.props(Props[ResourcesResponderV1]), RESOURCES_ROUTER_ACTOR_NAME)
    protected val valuesRouter = makeActor(FromConfig.props(Props[ValuesResponderV1]), VALUES_ROUTER_ACTOR_NAME)
    protected val sipiRouter = makeActor(FromConfig.props(Props[SipiResponderV1]), SIPI_ROUTER_ACTOR_NAME)
    protected val usersRouter = makeActor(FromConfig.props(Props[UsersResponderV1]), USERS_ROUTER_ACTOR_NAME)
    protected val listsRouter = makeActor(FromConfig.props(Props[HierarchicalListsResponderV1]), HIERARCHICAL_LISTS_ROUTER_ACTOR_NAME)
    protected val searchRouter = makeActor(FromConfig.props(Props[SearchResponderV1]), SEARCH_ROUTER_ACTOR_NAME)
    protected val ontologyRouter = makeActor(FromConfig.props(Props[OntologyResponderV1]), ONTOLOGY_ROUTER_ACTOR_NAME)
    protected val projectsRouter = makeActor(FromConfig.props(Props[ProjectsResponderV1]), PROJECTS_ROUTER_ACTOR_NAME)
    protected val ckanRouter = makeActor(FromConfig.props(Props[CkanResponderV1]), CKAN_ROUTER_ACTOR_NAME)

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
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }
}
