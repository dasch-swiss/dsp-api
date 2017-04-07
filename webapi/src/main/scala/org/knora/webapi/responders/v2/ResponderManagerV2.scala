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

package org.knora.webapi.responders.v2

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingReceive
import akka.routing.FromConfig
import org.knora.webapi.ActorMaker
import org.knora.webapi.messages.v2.responder.usermessages.{UsersResponderExternalRequestV2, UsersResponderInternalRequestV2}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1._
import org.knora.webapi.util.ActorUtil.handleUnexpectedMessage

/**
  * This actor receives messages representing client requests, and forwards them to pools specialised actors that it supervises.
  */
class ResponderManagerV2 extends Actor with ActorLogging {
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
      * Constructs the default Akka routing actor that routes messages to [[UsersResponderV1]].
      */
    protected final def makeDefaultUsersRouter = makeActor(FromConfig.props(Props[UsersResponderV2]), USERS_ROUTER_V1_ACTOR_NAME)

    /**
      * The Akka routing actor that should receive messages addressed to the users responder. Subclasses can override this
      * member to substitute a custom actor instead of the default users responder.
      */
    protected val usersRouter = makeDefaultUsersRouter


    def receive = LoggingReceive {
        case usersResponderRequest: UsersResponderExternalRequestV2 => usersRouter forward usersResponderRequest
        case usersResponderRequest: UsersResponderInternalRequestV2 => usersRouter forward usersResponderRequest
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }
}
