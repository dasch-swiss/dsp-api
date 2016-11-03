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

package org.knora.webapi.http

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.HttpOriginRange
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.{CorsSettings, HttpHeaderRange}

object CORSSupport {

    val corsSettings = CorsSettings.defaultSettings.copy(
        allowGenericHttpRequests = true,
        allowCredentials = true,
        allowedOrigins = HttpOriginRange.*,
        allowedHeaders = HttpHeaderRange.*,
        allowedMethods = List(GET, PUT, POST, DELETE, HEAD, OPTIONS),
        maxAge = Some(30 * 60)
    )

    /**
      * Adds CORS support to a route. This is a convenience method.
      * @param route the route for which CORS support is enabled
      * @return the enabled route.
      */
    def CORS(route: Route): Route = cors(corsSettings) {
        route
    }
}