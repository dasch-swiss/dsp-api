/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import zio.prelude.Validation

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter

/**
 * Data that needs to be passed to each route.
 *
 * @param system      the actor system.
 * @param appActor    the main application actor.
 * @param appConfig   the application's configuration.
 */
case class KnoraRouteData(system: akka.actor.ActorSystem, appActor: akka.actor.ActorRef, appConfig: AppConfig)

/**
 * An abstract class providing functionality that is commonly used in implementing Knora routes.
 *
 * @param routeData   a [[KnoraRouteData]] providing access to the application.
 */
abstract class KnoraRoute(routeData: KnoraRouteData) {

  implicit protected val system: ActorSystem                = routeData.system
  implicit protected val timeout: Timeout                   = routeData.appConfig.defaultTimeoutAsDuration
  implicit protected val executionContext: ExecutionContext = system.dispatcher
  implicit protected val stringFormatter: StringFormatter   = StringFormatter.getGeneralInstance
  implicit protected val appActor: ActorRef                 = routeData.appActor
  protected val log: Logger                                 = Logger(this.getClass)

  /**
   * Constructs a route.
   *
   * @return a route.
   */
  def makeRoute: Route

  /**
   * Helper method converting a [[zio.prelude.Validation]] to a [[scala.concurrent.Future]].
   */
  def toFuture[A](validation: Validation[Throwable, A]): Future[A] =
    validation.fold(
      errors => {
        val newThrowable: Throwable = errors.tail.foldLeft(errors.head) { (acc, err) =>
          acc.addSuppressed(err)
          acc
        }
        Future.failed(newThrowable)
      },
      Future.successful
    )
}
