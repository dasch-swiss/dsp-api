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

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.messages.app.appmessages.AppState.AppState
import org.knora.webapi.messages.app.appmessages.{AppState, GetAppState}
import org.knora.webapi.{Settings, SettingsImpl}

import scala.concurrent.{Await, ExecutionContextExecutor}

/**
  * A route used for rejecting requests to certain paths depending on the state of the app or the configuration.
  *
  * If the current state of the application is anything other then [[AppState.Running]], then return [[ServiceUnavailable]].
  * If the current state of the application is [[AppState.Running]], then reject requests to paths as defined
  * in 'application.conf'.
  */
class RejectingRoute(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter, applicationStateActor: ActorRef) {

    def knoraApiPath(): Route = {

        implicit val system: ActorSystem = _system
        implicit val executionContext: ExecutionContextExecutor = system.dispatcher
        implicit val timeout: Timeout = settings.defaultTimeout

        path(Remaining) { wholepath =>
                requestContext => {
                    log.debug(s"got request: ${requestContext.toString}")
                    val settings = Settings(system)
                    val reject: Seq[Option[Boolean]] = settings.routesToReject.map { pathToReject =>
                        if (wholepath.contains(pathToReject.toCharArray)) {
                            Some(true)
                        } else {
                            None
                        }
                    }

                    // get the current application state
                    val appState: AppState = Await.result(applicationStateActor ? GetAppState, timeout.duration).asInstanceOf[AppState]

                    // only if application state is 'Running' then go on and apply filter
                    if (appState == AppState.Running) {
                        if (reject.flatten.nonEmpty) {
                            requestContext.complete(NotFound, "The requested path is deactivated.")
                        } else {
                            requestContext.reject()
                        }
                    } else { // if any state other then 'Running', then return ServiceUnavailable
                        requestContext.complete(ServiceUnavailable, s"Application not available at the moment (state = $appState). Please try again later.")
                    }
                }
        }
    }
}