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
/*
package org.knora.webapi.http

import akka.actor._
import akka.http.scaladsl.server.Route
import org.apache.http.protocol.HttpService
import org.knora.webapi.Settings
import org.knora.webapi.routing.v1._

/**
  * An Actor that receives HTTP requests and dispatches them to `spray-routing` routes.
  */
class KnoraHttpService extends Actor with ActorLogging with HttpService with CORSSupport {

    private val system = context.system
    private val settings = Settings(system)

    private val apiRoutes: Route =
        ResourcesRouteV1.knoraApiPath(system, settings, log) ~
            ValuesRouteV1.knoraApiPath(system, settings, log) ~
            SipiRouteV1.knoraApiPath(system, settings, log) ~
            ListsRouteV1.knoraApiPath(system, settings, log) ~
            SearchRouteV1.knoraApiPath(system, settings, log) ~
            ResourceTypesRouteV1.knoraApiPath(system, settings, log) ~
            AuthenticateRouteV1.knoraApiPath(system, settings, log) ~
            AssetsRouteV1.knoraApiPath(system, settings, log) ~
            GraphDataRouteV1.knoraApiPath(system, settings, log) ~
            ProjectsRouteV1.knoraApiPath(system, settings, log) ~
            CkanRouteV1.knoraApiPath(system, settings, log) ~
            StoreRouteV1.knoraApiPath(system, settings, log)

    def actorRefFactory = context

    def receive = allowAllHosts orElse apiRoutes
}
*/