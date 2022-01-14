/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.util.Timeout
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.app.appmessages.{AppState, AppStates, GetAppState}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * Provides AppState actor access logic
 */
trait AppStateAccess {
  this: RejectingRoute =>

  override implicit val timeout: Timeout = 2998.millis

  protected def getAppState: Future[AppState] =
    for {

      state <- (applicationActor ? GetAppState()).mapTo[AppState]

    } yield state

}

/**
 * A route used for rejecting requests to certain paths depending on the state of the app or the configuration.
 *
 * If the current state of the application is anything other then [[AppStates.Running]], then return [[StatusCodes.ServiceUnavailable]].
 * If the current state of the application is [[AppStates.Running]], then reject requests to paths as defined
 * in 'application.conf'.
 */
class RejectingRoute(routeData: KnoraRouteData) extends KnoraRoute(routeData) with AppStateAccess {

  /**
   * Returns the route.
   */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(Remaining) { wholePath =>
      // check to see if route is on the rejection list
      val rejectSeq: Seq[Option[Boolean]] = settings.routesToReject.map { pathToReject: String =>
        if (wholePath.contains(pathToReject.toCharArray)) {
          Some(true)
        } else {
          None
        }
      }

      onComplete(getAppState) {

        case Success(appState) =>
          appState match {
            case AppStates.Running if rejectSeq.flatten.nonEmpty =>
              // route not allowed. will complete request.
              val msg = s"Request to $wholePath not allowed as per configuration for routes to reject."
              log.info(msg)
              complete(StatusCodes.NotFound, "The requested path is deactivated.")

            case AppStates.Running if rejectSeq.flatten.isEmpty =>
              // route is allowed. by rejecting, I'm letting it through so that some other route can match
              reject()

            case other =>
              // if any state other then 'Running', then return ServiceUnavailable
              val msg =
                s"Request to $wholePath rejected. Application not available at the moment (state = $other). Please try again later."
              log.info(msg)
              complete(StatusCodes.ServiceUnavailable, msg)
          }

        case Failure(ex) =>
          log.error("RejectingRoute - ex: {}", ex)
          complete(StatusCodes.ServiceUnavailable, ex.getMessage)
      }
    }
}
