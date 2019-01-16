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

package org.knora.webapi.routing

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{KnoraDispatchers, Settings, SettingsImpl}

import scala.concurrent.ExecutionContext

/**
  * An abstract class providing values that are commonly used in Knora responders.
  */
abstract class KnoraRoute(_system: ActorSystem, _applicationStateActor: ActorRef, _responderManager: ActorRef, _storeManager: ActorRef, _log: LoggingAdapter) {


    /* define implicits */
    implicit protected val system: ActorSystem = _system
    implicit protected val responderManager: ActorRef = _responderManager
    implicit protected val settings: SettingsImpl = Settings(system)
    implicit protected val timeout: Timeout = settings.defaultTimeout
    implicit protected val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    /* other members */
    protected val applicationStateActor: ActorRef = _applicationStateActor
    protected val storeManager: ActorRef = _storeManager
    protected val log: LoggingAdapter = _log

    /* instantiate stringFormater */
    protected val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /* define required method */
    protected def knoraApiPath: Route
}
