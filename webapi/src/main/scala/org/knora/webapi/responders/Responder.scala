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
import org.knora.webapi.Settings

import scala.language.postfixOps

/**
  * A trait providing values that are commonly used in Knora API v1 responders.
  */
trait Responder extends Actor with ActorLogging {
    /**
      * The responder's Akka actor system.
      */
    protected implicit val system = context.system

    /**
      * The application settings.
      */
    protected val settings = Settings(context.system)

    /**
      * The application's default timeout for `ask` messages.
      */
    protected implicit val timeout = settings.defaultRestoreTimeout

    /**
      * The Akka actor system's execution context for futures.
      */
    protected implicit val executionContext = system.dispatcher

    /**
      * A reference to the Knora API v1 responder manager.
      */
    protected val responderManager = context.actorSelection("/user/responderManager")

    /**
      * A reference to the Knora API v1 responder manager.
      */
    protected val responderManager2 = context.actorSelection("/user/responderManager2")

    /**
      * A reference to the store manager.
      */
    protected val storeManager = context.actorSelection("/user/storeManager")
}
