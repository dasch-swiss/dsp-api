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

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import org.knora.webapi.messages.v1.responder.V1Request
import org.knora.webapi.messages.v2.responder.V2Request
import org.knora.webapi.util.ActorUtil.handleUnexpectedMessage

/**
  * This actor receives messages representing client requests, and forwards them to pools specialised actors that it supervises.
  */
class ResponderVersionRouter extends Actor with ActorLogging {

    /**
      * The responder's Akka actor system.
      */
    implicit val system = context.system

    /**
      * The Akka actor system's execution context for futures.
      */
    implicit val executionContext = system.dispatcher

    val responderManagerV1 = context.actorSelection(RESPONDER_MANAGER_V1_ACTOR_PATH)
    val responderManagerV2 = context.actorSelection(RESPONDER_MANAGER_V2_ACTOR_PATH)

    def receive = LoggingReceive {
        case v1Messages: V1Request => responderManagerV1 forward v1Messages
        case v2Messages: V2Request => responderManagerV2 forward v2Messages
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }
}
