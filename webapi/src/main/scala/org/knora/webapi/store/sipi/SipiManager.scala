/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.sipi

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import akka.event.LoggingReceive
import akka.routing.FromConfig
import org.knora.webapi._
import org.knora.webapi.messages.store.sipimessages.SipiRequest
import org.knora.webapi.store.SipiConnectorActorName

/**
  * Makes requests to Sipi.
  */
class SipiManager extends Actor with ActorLogging {
    this: ActorMaker =>

    /**
      * Constructs the [[SipiConnector]] actor (pool).
      */
    protected final def makeDefaultSipiConnector: ActorRef = makeActor(FromConfig.props(Props[SipiConnector]).withDispatcher(KnoraDispatchers.KnoraActorDispatcher), SipiConnectorActorName)

    /**
      * Subclasses can override this member to substitute a custom actor instead of the default SipiConnector.
      */
    protected lazy val sipiConnector: ActorRef = makeDefaultSipiConnector

    def receive = LoggingReceive {
        case sipiMessages: SipiRequest => sipiConnector forward sipiMessages
        case other => sender ! Status.Failure(UnexpectedMessageException(s"SipiManager received an unexpected message: $other"))
    }

}
