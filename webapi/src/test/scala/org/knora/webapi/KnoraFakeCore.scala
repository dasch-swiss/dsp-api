/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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
