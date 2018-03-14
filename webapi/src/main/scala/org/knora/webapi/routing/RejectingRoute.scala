/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.{Settings, SettingsImpl}
import akka.http.scaladsl.model.StatusCodes._

/**
  * A route used for faking the image server.
  */
object RejectingRoute {

    def knoraApiPath(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter): Route = {
        implicit val system = _system
        implicit val executionContext = system.dispatcher
        implicit val timeout = settings.defaultTimeout

        path(Remaining) { wholepath =>
                requestContext => {
                    log.debug(s"got request: ${requestContext.toString}")
                    val settings = Settings(system)
                    val reject: Seq[Option[Boolean]] = settings.routesToReject.map { pathtoreject =>
                        if (wholepath.contains(pathtoreject.toCharArray)) {
                            Some(true)
                        } else {
                            None
                        }
                    }
                    if (reject.flatten.nonEmpty) {
                        requestContext.complete(NotFound, "The requested path is deactivated.")
                    } else {
                        requestContext.reject()
                    }
                }
        }
    }
}