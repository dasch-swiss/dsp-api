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
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectDataGraphsGetADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.datamanagement.{DataUpgradeCheck, DataUpgradeCheckResult, DataUpgradeInit, DataUpgradeInitResult}
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_PATH
import org.knora.webapi.util.ActorUtil.future2Message

import scala.concurrent.{ExecutionContextExecutor, Future}

/**
  * This actor handles data upgrade.
  */
class DataUpgradeActor extends Actor with ActorLogging {

    private val responderManager = context.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)

    private val settings = Settings(context.system)

    implicit val executionContext: ExecutionContextExecutor = context.system.dispatcher

    // Need to keep an eye on this one. We probably need to tweek it and maybe have
    // an own property in application.conf
    implicit val timeout: Timeout = settings.defaultRestoreTimeout

    def receive = LoggingReceive {
        case DataUpgradeCheck(requestingUser) =>
        case DataUpgradeInit(liveMode, requestingUser) => future2Message(sender(), init(liveMode, requestingUser), log)
    }


    /**
      * Initiates a data upgrade check.
      *
      * @param requestingUser the user making the request. Needs to be knora-admin:SystemAdmin.
      * @return
      */
    private def check(requestingUser: UserADM): Future[DataUpgradeCheckResult] = {

        if (requestingUser.id != OntologyConstants.KnoraAdmin.SystemUser) {
            throw ForbiddenException(s"Only allowed to be called by the '${OntologyConstants.KnoraAdmin.SystemUser}'.")
        }

        ???
    }

    /**
      * Initiates a data upgrade run.
      *
      * @param liveMode the mode of the run.
      * @param requestingUser the user making the request. Needs to be knora-admin:SystemAdmin.
      * @return
      */
    private def init(liveMode: Boolean, requestingUser: UserADM): Future[DataUpgradeInitResult] = {

        if (requestingUser.id != OntologyConstants.KnoraAdmin.SystemUser) {
            throw ForbiddenException(s"Only allowed to be called by the '${OntologyConstants.KnoraAdmin.SystemUser}'.")
        }

        for {


        // get all project data graphs
            projectGraphs <- (responderManager ? ProjectDataGraphsGetADM(KnoraSystemInstances.Users.SystemUser)).mapTo[Seq[IRI]]


        // include the admin data graph and permissions data graph

        // check version of each data graph

        //

        } yield ???
    }
}
