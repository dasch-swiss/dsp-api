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

package org.knora.webapi.http

import akka.actor.{Actor, ActorLogging, Props, Status}
import akka.event.LoggingReceive
import akka.io.Tcp.Connected
import akka.routing.FromConfig
import org.knora.webapi.{ActorMaker, UnexpectedMessageException}

/**
  * This actor receives HTTP requests and forwards them to a pool of [[KnoraHttpService]] instances.
  */
class KnoraHttpServiceManager extends Actor with ActorLogging {
    this: ActorMaker =>

    private val knoraHttpServiceRouter = context.actorOf(FromConfig.props(Props[KnoraHttpService]), KNORA_HTTP_SERVICE_ROUTER_ACTOR_NAME)

    def receive = LoggingReceive {
        case connected: Connected => knoraHttpServiceRouter.forward(connected)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }
}
