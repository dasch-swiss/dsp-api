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

package org.knora.webapi.routing

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.util.Timeout
import com.github.everpeace.healthchecks._
import com.github.everpeace.healthchecks.route._
import org.knora.webapi.SettingsImpl
import org.knora.webapi.messages.app.appmessages.AppState.AppState
import org.knora.webapi.messages.app.appmessages.{AppState, GetAppState}

import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._

/**
  * Provides the '/health' endpoint serving the health status.
  */
class HealthRoute(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter, applicationStateActor: ActorRef) {

    implicit val system: ActorSystem = _system
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    implicit private val timeout: Timeout = Timeout(100.milliseconds)

    val appState = healthCheck("appState") {

        val state: AppState = Await.result(applicationStateActor ? GetAppState, 1.seconds).asInstanceOf[AppState]
        state match {
            case AppState.StartingUp => unhealthy("Starting up. Please retry later.")
            case AppState.WaitingForDB => unhealthy("Waiting for DB. Please retry later.")
            case AppState.DBReady => unhealthy("DB ready. Please retry later.")
            case AppState.LoadingOntologies => unhealthy("Loading ontologies. Please retry later.")
            case AppState.OntologiesReady => unhealthy("Ontologies ready. Please retry later.")
            case AppState.MaintainanceMode => unhealthy("Application is in maintenance mode. Please retry later.")
            case AppState.Running => healthy
        }
    }

    def knoraApiPath: Route = {
        HealthCheckRoutes.health(appState)
    }

}
