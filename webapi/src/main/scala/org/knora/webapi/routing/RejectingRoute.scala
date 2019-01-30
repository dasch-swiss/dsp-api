/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import org.knora.webapi.messages.app.appmessages.AppState.AppState
import org.knora.webapi.messages.app.appmessages.{AppState, GetAppState}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


/**
  * Provides AppState actor access logic
  */
trait AppStateAccess {
    this: RejectingRoute =>

    override implicit val timeout: Timeout = 1.second

    protected def getAppState(): Future[AppState] = for {

        state <- (applicationStateActor ? GetAppState()).mapTo[AppState]

    } yield state

}

/**
  * A route used for rejecting requests to certain paths depending on the state of the app or the configuration.
  *
  * If the current state of the application is anything other then [[AppState.Running]], then return [[StatusCodes.ServiceUnavailable]].
  * If the current state of the application is [[AppState.Running]], then reject requests to paths as defined
  * in 'application.conf'.
  */
class RejectingRoute(routeData: KnoraRouteData) extends KnoraRoute(routeData) with AppStateAccess {

    def knoraApiPath: Route = {

        path(Remaining) { wholepath =>

            // check to see if route is on the rejection list
            val rejectSeq: Seq[Option[Boolean]] = settings.routesToReject.map { pathToReject: String =>
                if (wholepath.contains(pathToReject.toCharArray)) {
                    Some(true)
                } else {
                    None
                }
            }

            onComplete(getAppState()) {

                case Success(appState) => {

                    appState match {
                        case AppState.Running if rejectSeq.flatten.nonEmpty => {
                            // route not allowed. will complete request.
                            val msg = s"Request to $wholepath not allowed as per configuration for routes to reject."
                            log.info(msg)
                            complete(StatusCodes.NotFound, "The requested path is deactivated.")
                        }
                        case AppState.Running if rejectSeq.flatten.isEmpty => {
                            // route is allowed. by rejecting, I'm letting it through so that some other route can match
                            reject()
                        }
                        case other => {
                            // if any state other then 'Running', then return ServiceUnavailable
                            val msg = s"Request to $wholepath rejected. Application not available at the moment (state = $other). Please try again later."
                            log.info(msg)
                            complete(StatusCodes.ServiceUnavailable, msg)
                        }
                    }
                }

                case Failure(ex) => {
                    log.error("RejectingRoute - ex: {}", ex)
                    complete(StatusCodes.ServiceUnavailable, ex.getMessage)
                }
            }
        }
    }
}