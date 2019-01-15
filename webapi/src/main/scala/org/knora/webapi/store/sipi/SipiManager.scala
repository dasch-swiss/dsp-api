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

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive
import akka.routing.FromConfig
import org.knora.webapi._
import org.knora.webapi.responders.v1.SipiResponderV1
import org.knora.webapi.store.SipiConnectorActorName
import org.knora.webapi.util.StringFormatter

import scala.concurrent.ExecutionContext

/**
  * Makes requests to Sipi.
  */
class SipiManager extends Actor with ActorLogging {
    this: ActorMaker =>


    implicit val system: ActorSystem = context.system
    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    val settings = Settings(system)

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * Constructs the [[SipiResponderV1]] actor pool.
      */
    protected final def makeDefaultSipiConnectorPool: ActorRef = makeActor(FromConfig.props(Props[SipiConnector]).withDispatcher(KnoraDispatchers.KnoraActorDispatcher), SipiConnectorActorName)

    /**
      * The Akka routing actor that should receive messages addressed to the Sipi responder. Subclasses can override this
      * member to substitute a custom actor instead of the default Sipi responder.
      */
    protected lazy val sipiConnector: ActorRef = makeDefaultSipiConnectorPool

    def receive = LoggingReceive {
        case msg ⇒ sipiConnector forward msg
    }

}
