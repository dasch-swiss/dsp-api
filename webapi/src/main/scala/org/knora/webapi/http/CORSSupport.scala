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

package org.knora.webapi.http

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.HttpOriginRange
import akka.http.scaladsl.server.{Directives, RejectionHandler, Route}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.model.HttpHeaderRange
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import org.knora.webapi.{KnoraExceptionHandler, SettingsImpl}

import scala.collection.immutable.Seq

object CORSSupport extends Directives {

    val age: Long = 30 * 60

    val corsSettings = CorsSettings.defaultSettings.
            withAllowGenericHttpRequests(true).
            withAllowCredentials(true).
            withAllowedOrigins(HttpOriginRange.*).
            withAllowedHeaders(HttpHeaderRange.*).
            withAllowedMethods(Seq(GET, PUT, POST, DELETE, HEAD, OPTIONS)).
            withExposedHeaders(Seq.empty).
            withMaxAge(Some(age))

    /**
      * Adds CORS support to a route. Also, any exceptions thrown inside the route are handled by
      * the [[KnoraExceptionHandler]]. Finally, all rejections are handled by the [[CorsDirectives.corsRejectionHandler]]
      * so that all responses (correct and failures) have the correct CORS headers attached.
      *
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