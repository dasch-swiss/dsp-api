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

package org.knora.webapi.http

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.HttpOriginRange
import akka.http.scaladsl.server.{Directives, RejectionHandler, Route}
import ch.megard.akka.http.cors.CorsDirectives._
import ch.megard.akka.http.cors.{CorsDirectives, CorsSettings, HttpHeaderRange}
import org.knora.webapi.{KnoraExceptionHandler, SettingsImpl}

import scala.collection.immutable.Seq

object CORSSupport extends Directives {

    val corsSettings = CorsSettings.Default(
        allowGenericHttpRequests = true,
        allowCredentials = true,
        allowedOrigins = HttpOriginRange.*,
        allowedHeaders = HttpHeaderRange.*,
        allowedMethods = Seq(GET, PUT, POST, DELETE, HEAD, OPTIONS),
        exposedHeaders = Seq.empty,
        maxAge = Some(30 * 60)
    )

    /**
      * Adds CORS support to a route. Also, any exceptions thrown inside the route are handled by
      * the [[KnoraExceptionHandler]]. Finally, all rejections are handled by the [[CorsDirectives.corsRejectionHandler]]
      * so that all responses (correct and failures) have the correct CORS headers attached.
      * @param route the route for which CORS support is enabled
      * @return the enabled route.
      */
    def CORS(route: Route, settings: SettingsImpl, log: LoggingAdapter): Route = {
        handleRejections(CorsDirectives.corsRejectionHandler) {
            cors(corsSettings) {
                handleRejections(RejectionHandler.default) {
                    handleExceptions(KnoraExceptionHandler(settings, log)) {
                        route
                    }
                }
            }
        }
    }
}