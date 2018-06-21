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
import akka.dispatch.MessageDispatcher
import akka.event.LoggingAdapter
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{get, path}
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.SettingsImpl
import org.knora.webapi.messages.app.appmessages.AppState.AppState
import org.knora.webapi.messages.app.appmessages.{AppState, GetAppState}
import spray.json.{JsObject, JsString}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}


case class HealthCheckResult(name: String,
                             severity: String,
                             status: Boolean,
                             message: String)

/**
  * Provides health check logic
  */
trait HealthCheck {
    this: HealthRoute =>

    implicit private val timeout: Timeout = Timeout(1.seconds)

    protected def healthcheck(): HealthCheckResult = {

        implicit val blockingDispatcher: MessageDispatcher = system.dispatchers.lookup("my-blocking-dispatcher")
        implicit val executor: ExecutionContext = blockingDispatcher

        val state = Await.result(applicationStateActor ? GetAppState(), 1.second).asInstanceOf[AppState]

        state match {
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
    }

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
class HealthRoute(_system: ActorSystem, settings: SettingsImpl, _log: LoggingAdapter, _applicationStateActor: ActorRef) extends HealthCheck{

    implicit val system: ActorSystem = _system
    implicit val executionContext: ExecutionContext = system.dispatcher



    val applicationStateActor: ActorRef = _applicationStateActor
    val log = _log

    def knoraApiPath: Route = {
        path("health") {
            get {
                requestContext => {
                    requestContext.complete {
                        createResponse(healthcheck())
                    }
                }
            }
        }
    }
}
