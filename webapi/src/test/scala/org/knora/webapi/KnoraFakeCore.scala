/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
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

package org.knora.webapi

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.app.{ApplicationActor, LiveManagers}
import org.knora.webapi.core.Core
import org.knora.webapi.settings.APPLICATION_MANAGER_ACTOR_NAME

import scala.concurrent.duration.FiniteDuration

/**
 * A fake Knora service that Sipi can use to get file permissions.
 */
trait KnoraFakeCore {
  this: Core =>

  // need to create an actor to conform to Core but never send AppStart()
  override val appActor: ActorRef =
    system.actorOf(Props(new ApplicationActor with LiveManagers), name = APPLICATION_MANAGER_ACTOR_NAME)

  /**
   * Timeout definition (need to be high enough to allow reloading of data so that checkActorSystem doesn't timeout)
   */
  implicit private val timeout: FiniteDuration = settings.defaultTimeout

  /**
   * Faked `webapi` routes
   */
  private val apiRoutes = {
    path("admin" / "files" / Segments(2)) { projectIDAndFile =>
      get {
        complete(
          """
                    {
                        "permissionCode": 2,
                        "status": 0
                    }
                    """
        )
      }
    }
  }

  /**
   * Starts the Faked Knora API server.
   */
  def startService(): Unit = {
    Http().newServerAt(settings.internalKnoraApiHost, settings.internalKnoraApiPort).bindFlow(Route.toFlow(apiRoutes))
    println(
      s"Faked Knora API Server started at http://${settings.internalKnoraApiHost}:${settings.internalKnoraApiPort}."
    )
  }

  /**
   * Stops Knora.
   */
  def stopService(): Unit =
    system.terminate()

}
