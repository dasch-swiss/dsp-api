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
import akka.http.scaladsl.server.{Directives, ExceptionHandler, RejectionHandler, Route}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import com.typesafe.scalalogging.StrictLogging
import org.knora.webapi.{KnoraExceptionHandler, KnoraSettings}

object CORSSupport extends Directives with StrictLogging {

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
    def CORS(route: Route, system: ActorSystem): Route = {

        // Our rejection handler. Here we are using the default one.
        val rejectionHandler: RejectionHandler = CorsDirectives.corsRejectionHandler

        // Our exception handler
        val exceptionHandler: ExceptionHandler = KnoraExceptionHandler(KnoraSettings(system))

        // Combining the two handlers only for convenience
        val handleErrors = handleRejections(rejectionHandler) & handleExceptions(exceptionHandler)

        // cors settings
        val corsSettings = CorsSettings(system)

        handleErrors {
            println(s"CORS settings: $corsSettings")
            CorsDirectives.cors(corsSettings) {
                handleErrors {
                    route
                }
            }
        }
    }
}