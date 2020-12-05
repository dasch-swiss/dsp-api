/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.http.directives

import akka.actor.ActorSystem
import akka.http.scaladsl.server
import akka.http.scaladsl.server.Directives.{handleExceptions, handleRejections}
import akka.http.scaladsl.server.{ExceptionHandler, RejectionHandler}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import org.knora.webapi.http.handler.KnoraExceptionHandler
import org.knora.webapi.settings.KnoraSettings

/**
 * DSP-API HTTP directives, used by wrapping around a routes, to influence
 * rejections and exception handling
 */
object DSPApiDirectives {

    // Our rejection handler. Here we are using the default one from the CORS lib
    def rejectionHandler: RejectionHandler = CorsDirectives.corsRejectionHandler.withFallback(RejectionHandler.default)

    // Our exception handler
    def exceptionHandler(system: ActorSystem): ExceptionHandler = KnoraExceptionHandler(KnoraSettings(system))

    // Combining the two handlers for convenience
    def handleErrors(system: ActorSystem): server.Directive[Unit] = handleRejections(rejectionHandler) & handleExceptions(exceptionHandler(system))
}
