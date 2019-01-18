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
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{KnoraDispatchers, Settings, SettingsImpl}

import scala.concurrent.ExecutionContext


/**
  * Data needed to be passed to each route.
  *
  * @param system the actor system.
  * @param applicationStateActor the application state actor ActorRef.
  * @param responderManager the responder manager ActorRef.
  * @param storeManager the store manager ActorRef.
  */
case class KnoraRouteData(system: ActorSystem, applicationStateActor: ActorRef, responderManager: ActorRef, storeManager: ActorRef)


/**
  * An abstract class providing values that are commonly used in Knora responders.
  */
abstract class KnoraRoute(routeData: KnoraRouteData) {

    implicit protected val system: ActorSystem = routeData.system
    implicit protected val responderManager: ActorRef = routeData.responderManager
    implicit protected val settings: SettingsImpl = Settings(system)
    implicit protected val timeout: Timeout = settings.defaultTimeout
    implicit protected val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
    implicit protected val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    protected val applicationStateActor: ActorRef = routeData.applicationStateActor
    protected val storeManager: ActorRef = routeData.storeManager
    protected val log = akka.event.Logging(system, this.getClass)

    /**
      * Returns the route. Needs to be implemented in each subclass.
      * @return [[Route]]
      */
    def knoraApiPath: Route
}
