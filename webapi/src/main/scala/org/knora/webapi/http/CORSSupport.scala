/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, RejectionHandler, Route}
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.{KnoraExceptionHandler, SettingsImpl}

object CORSSupport extends Directives with LazyLogging {

    /**
     * Adds CORS support to a route with the following:
     *  - any exceptions thrown inside the route are handled by the
     *    [[KnoraExceptionHandler]],
     *  - all rejections are handled by the [[CorsDirectives.corsRejectionHandler]]
     *    so that all responses (correct and failures) have the correct CORS
     *    headers attached, and
     *  - the settings for CORS are set in application.conf under "akka-http-cors".
     *
     * @param route the route for which CORS support is enabled
     * @return the enabled route.
     */
    def CORS(route: Route, settings: SettingsImpl, system: ActorSystem): Route = {
        handleRejections(CorsDirectives.corsRejectionHandler) {
            cors(CorsSettings(system)) {
                handleRejections(RejectionHandler.default) {
                    handleExceptions(KnoraExceptionHandler(settings)) {
                        route
                    }
                }
            }
        }
    }
}