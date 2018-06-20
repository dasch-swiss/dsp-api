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
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.util.Timeout
import com.github.everpeace.healthchecks.HealthCheck
import com.github.everpeace.healthchecks.route.HealthCheckRoutes
import io.circe.generic.JsonCodec
import org.knora.webapi.SettingsImpl
import org.knora.webapi.messages.app.appmessages.AppState.AppState
import org.knora.webapi.messages.app.appmessages.{AppState, GetAppState}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileTypeV1
import spray.json.{JsNumber, JsObject, JsString}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}


@JsonCodec case class HealthCheckResult(name: String,
                                        severity: String,
                                        status: String,
                                        messages: String)

/**
  * Provides the '/health' endpoint serving the health status.
  */
class HealthRoute(_system: ActorSystem, settings: SettingsImpl, log: LoggingAdapter, applicationStateActor: ActorRef) {

    implicit val system: ActorSystem = _system
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    implicit private val timeout: Timeout = Timeout(100.milliseconds)

    val healthcheck = {

        val state: AppState = Await.result(applicationStateActor ? GetAppState, 1.seconds).asInstanceOf[AppState]
        state match {
            case AppState.StartingUp => unhealthy("Starting up. Please retry later.")
            case AppState.WaitingForRepository => unhealthy("Waiting for Repository. Please retry later.")
            case AppState.RepositoryReady => unhealthy("Repository ready. Please retry later.")
            case AppState.CreatingCaches => unhealthy("Creating caches. Please retry later.")
            case AppState.CachesReady => unhealthy("Caches ready. Please retry later.")
            case AppState.LoadingOntologies => unhealthy("Loading ontologies. Please retry later.")
            case AppState.OntologiesReady => unhealthy("Ontologies ready. Please retry later.")
            case AppState.MaintenanceMode => unhealthy("Application is in maintenance mode. Please retry later.")
            case AppState.Running => healthy
        }
    }

    def knoraApiPath: Route = {
        HealthCheckRoutes.health(appState)
    }

    private def status(s: Boolean) = if (s) "healthy" else "unhealthy"

    private def statusCode(s: Boolean) = if (s) StatusCodes.OK else StatusCodes.ServiceUnavailable

    private def toResultJson(check: HealthCheck, result: HealthCheckResult) =
        HealthCheckRoutes.HealthCheckResultJson(
            check.name,
            check.severity.toString,
            status(result.isValid),
            result match {
                case Valid(_)        => List()
                case Invalid(errors) => errors.toList
            }
        )

    private def createResponse(): HttpResponse = {
        HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(
                ContentTypes.`application/json`,
                JsObject(
                    "status" -> JsNumber(0),
                    "message" -> JsString("credentials are OK"),
                    "sid" -> JsString(sessionToken),
                    "userProfile" -> userProfile.ofType(UserProfileTypeV1.RESTRICTED).toJsValue
                ).compactPrint
            )
        )
    }

}
