/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import com.typesafe.scalalogging.Logger
import org.apache.pekko
import zio.*
import zio.prelude.Validation

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter

import pekko.actor.ActorRef
import pekko.actor.ActorSystem
import pekko.http.scaladsl.server.RequestContext
import pekko.http.scaladsl.server.Route
import pekko.util.Timeout

/**
 * Data that needs to be passed to each route.
 *
 * @param system    the actor system.
 * @param appActor  the main application actor.
 * @param appConfig the application's configuration.
 */
case class KnoraRouteData(system: ActorSystem, appActor: ActorRef, appConfig: AppConfig)

/**
 * An abstract class providing functionality that is commonly used in implementing Knora routes.
 *
 * @param routeData a [[KnoraRouteData]] providing access to the application.
 */
abstract class KnoraRoute(routeData: KnoraRouteData, protected implicit val runtime: Runtime[Authenticator]) {

  implicit protected val system: ActorSystem                = routeData.system
  implicit protected val timeout: Timeout                   = Timeout.create(routeData.appConfig.defaultTimeout)
  implicit protected val executionContext: ExecutionContext = system.dispatcher
  implicit protected val stringFormatter: StringFormatter   = StringFormatter.getGeneralInstance
  implicit protected val appActor: ActorRef                 = routeData.appActor

  protected val appConfig: AppConfig = routeData.appConfig
  protected val log: Logger          = Logger(this.getClass)

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
      Future.successful,
    )

  def getUserADM(ctx: RequestContext) = UnsafeZioRun.runToFuture(ZIO.serviceWithZIO[Authenticator](_.getUserADM(ctx)))
}
