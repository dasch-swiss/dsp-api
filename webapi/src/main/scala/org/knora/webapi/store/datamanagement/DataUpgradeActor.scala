/*
 * Copyright © 2015-2018 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.datamanagement

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectDataGraphsGetADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.datamanagement.{DataUpgradeInit, DataUpgradeResult}
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_PATH
import org.knora.webapi.util.ActorUtil.future2Message

import scala.concurrent.Future

/**
  * This actor handles data upgrade.
  */
class DataUpgradeActor extends Actor with ActorLogging {

    private val responderManager = context.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)

    private val settings = Settings(context.system)


    def receive = LoggingReceive {
        case DataUpgradeInit(requestingUser) => future2Message(sender(), init(requestingUser), log)
    }


    private def init(requestingUser: UserADM): Future[DataUpgradeResult] = {

        if (requestingUser.id != OntologyConstants.KnoraAdmin.SystemUser) {
            throw ForbiddenException(s"Only allowed to be called by the '${OntologyConstants.KnoraAdmin.SystemUser}'.")
        }

        for {


        // get all project data graphs
            projectGraphs <- (responderManager ? ProjectDataGraphsGetADM(KnoraSystemInstances.Users.SystemUser)).mapTo[Seq[IRI]]


        // include the admin data graph and permissions data graph

        // check version of each data graph

        //

        }

        ???
    }
}
