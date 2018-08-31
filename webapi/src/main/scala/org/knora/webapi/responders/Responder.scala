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

import akka.actor.{Actor, ActorLogging, ActorSelection, ActorSystem}
import akka.util.Timeout
import org.knora.webapi.Settings
import org.knora.webapi.app._
import org.knora.webapi.store._
import org.knora.webapi.util.StringFormatter

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps

/**
  * A trait providing values that are commonly used in Knora responders.
  */
trait Responder extends Actor with ActorLogging {
    /**
      * The responder's Akka actor system.
      */
    protected implicit val system: ActorSystem = context.system

    /**
      * The application settings.
      */
    protected val settings = Settings(system)

    /**
      * A string formatter.
      */
    protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * The application's default timeout for `ask` messages.
      */
    protected implicit val timeout: Timeout = settings.defaultTimeout

    /**
      * The Akka actor system's execution context for futures.
      */
    protected implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    /**
      * A reference to the application state actor.
      */
    protected val applicationStateActor: ActorSelection = context.actorSelection(APPLICATION_STATE_ACTOR_PATH)

    /**
      * A reference to the Knora API responder manager.
      */
    protected val responderManager: ActorSelection = context.actorSelection(RESPONDER_MANAGER_ACTOR_PATH)

    /**
      * A reference to the store manager.
      */
    protected val storeManager: ActorSelection = context.actorSelection(STORE_MANAGER_ACTOR_PATH)
}
