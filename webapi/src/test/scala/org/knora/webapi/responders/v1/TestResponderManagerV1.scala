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

import akka.actor._
import org.knora.webapi.responders._
import org.knora.webapi.{ActorMaker, LiveActorMaker}

/**
  * Extends [[ResponderManagerV1]] inheriting all the defined routes and allows for the overriding with a mock responder.
  *
  * @param mockResponders a Map containing the mock responders to be used instead of the live ones.
  *                       The name of the actor is used as the key in map.
  */
class TestResponderManagerV1(mockResponders: Map[String, ActorRef]) extends ResponderManagerV1 with LiveActorMaker {
    this: ActorMaker =>

    override val sipiRouter = if (mockResponders.contains(SIPI_ROUTER_ACTOR_NAME))
        mockResponders("sipiRouter")
    else
        // TODO: Actually, nothing has to be overridden here. Find a nicer way to handle this.
        makeActor(Props[SipiResponderV1], SIPI_ROUTER_ACTOR_NAME)


}
