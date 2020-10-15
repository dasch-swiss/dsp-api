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
import akka.stream.Materializer
import akka.util.Timeout
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl}

import scala.concurrent.ExecutionContext


/**
 * Data needed to be passed to each route.
 *
 * @param system   the actor system.
 * @param appActor the main application actor.
 */
case class KnoraRouteData(system: ActorSystem,
                          appActor: ActorRef)


/**
 * An abstract class providing values that are commonly used in Knora responders.
 */
abstract class KnoraRoute(routeData: KnoraRouteData) {

    implicit protected val system: ActorSystem = routeData.system
    implicit protected val settings: KnoraSettingsImpl = KnoraSettings(system)
    implicit protected val timeout: Timeout = settings.defaultTimeout
    implicit protected val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
    implicit protected val materializer: Materializer = Materializer.matFromSystem(system)
    implicit protected val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    protected val applicationActor: ActorRef = routeData.appActor
    implicit protected val responderManager: ActorRef = routeData.appActor
    protected val storeManager: ActorRef = routeData.appActor
    protected val log: LoggingAdapter = akka.event.Logging(system, this.getClass)
    protected val baseApiUrl: String = settings.internalKnoraApiBaseUrl

    /**
     * Returns the route. Needs to be implemented in each subclass.
     *
     * @return [[Route]]
     */
    def knoraApiPath: Route
}
