/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import akka.actor._
import org.knora.webapi.{ActorMaker, LiveActorMaker}

/**
  * A subclass of [[ResponderManager]] that allows tests to substitute custom responders for the standard ones.
  * Currently only supports a mock Sipi responder.
  *
  * @param mockResponders a [[Map]] containing the mock responders to be used instead of the live ones.
  *                       The name of the actor (a constant from [[org.knora.webapi.responders]] is used as the key in the map.
  */
class MockableResponderManager(mockResponders: Map[String, ActorRef]) extends ResponderManager with LiveActorMaker {
    this: ActorMaker =>

    /**
      * Initialised to the value of the key [[SIPI_ROUTER_V1_ACTOR_NAME]] in `mockResponders` if provided, otherwise
      * the default Akka router provided by the base class for the Sipi responder.
      */
    override lazy val sipiRouterV1 = mockResponders.getOrElse(SIPI_ROUTER_V1_ACTOR_NAME, makeDefaultSipiRouterV1)
}
