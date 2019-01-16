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

package org.knora.webapi.routing

import akka.actor.{ActorSelection, ActorSystem}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{get, path}
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.SettingsImpl
import org.knora.webapi.app.APPLICATION_STATE_ACTOR_PATH
import org.knora.webapi.messages.app.appmessages.AppState.AppState
import org.knora.webapi.messages.app.appmessages.{AppState, GetAppState}
import spray.json.{JsObject, JsString}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


case class HealthCheckResult(name: String,
                             severity: String,
                             status: Boolean,
                             message: String)

/**
  * Provides health check logic
  */
trait HealthCheck {
    this: HealthRoute =>

    implicit private val timeout: Timeout = 1.second

    protected def healthcheck(): Future[HttpResponse] = for {

        state <- (applicationStateActor ? GetAppState()).mapTo[AppState]

        result = state match {
            case AppState.Stopped => unhealthy("Stopped. Please retry later.")
            case AppState.StartingUp => unhealthy("Starting up. Please retry later.")
            case AppState.WaitingForRepository => unhealthy("Waiting for Repository. Please retry later.")
            case AppState.RepositoryReady => unhealthy("Repository ready. Please retry later.")
            case AppState.CreatingCaches => unhealthy("Creating caches. Please retry later.")
            case AppState.CachesReady => unhealthy("Caches ready. Please retry later.")
            case AppState.LoadingOntologies => unhealthy("Loading ontologies. Please retry later.")
            case AppState.OntologiesReady => unhealthy("Ontologies ready. Please retry later.")
            case AppState.MaintenanceMode => unhealthy("Application is in maintenance mode. Please retry later.")
            case AppState.Running => healthy()
        }

        response = createResponse(result)

    } yield response

    protected def createResponse(result: HealthCheckResult): HttpResponse = {

        HttpResponse(
            status = statusCode(result.status),
            entity = HttpEntity(
                ContentTypes.`application/json`,
                JsObject(
                    "name" -> JsString(result.name),
                    "severity" -> JsString(result.severity),
                    "status" -> JsString(status(result.status)),
                    "message" -> JsString(result.message)
                ).compactPrint
            )
        )
    }

    private def status(s: Boolean) = if (s) "healthy" else "unhealthy"

    private def statusCode(s: Boolean) = if (s) StatusCodes.OK else StatusCodes.ServiceUnavailable

    private def unhealthy(str: String) = {
        HealthCheckResult(
            name = "AppState",
            severity = "non fatal",
            status = false,
            message = str
        )
    }

    private def healthy() = {
        HealthCheckResult(
            name = "AppState",
            severity = "non fatal",
            status = true,
            message = "Application is heathy"
        )
    }
}

/**
  * Provides the '/health' endpoint serving the health status.
  */
class HealthRoute(_system: ActorSystem, settings: SettingsImpl) extends HealthCheck{

    implicit val system: ActorSystem = _system
    implicit val executionContext: ExecutionContext = system.dispatchers.defaultGlobalDispatcher
    protected val applicationStateActor: ActorSelection = system.actorSelection(APPLICATION_STATE_ACTOR_PATH)

    val log = akka.event.Logging(system, this.getClass)

    def knoraApiPath: Route = {
        path("health") {
            get {
                requestContext =>
                    requestContext.complete(healthcheck())
            }
        }
    }
}
