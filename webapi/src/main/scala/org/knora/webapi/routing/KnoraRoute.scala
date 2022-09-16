/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import zio.prelude.Validation

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import dsp.errors.BadRequestException
import org.knora.webapi.IRI
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

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
   * Gets a [[ProjectADM]] corresponding to the specified project IRI.
   *
   * @param projectIri           the project IRI.
   * @param requestingUser       the user making the request.
   * @return the corresponding [[ProjectADM]].
   */
  protected def getProjectADM(
    projectIri: IRI,
    requestingUser: UserADM
  ): Future[ProjectADM] = {
    val checkedProjectIri = stringFormatter.validateAndEscapeProjectIri(
      projectIri,
      throw BadRequestException(s"Invalid project IRI: $projectIri")
    )

    if (stringFormatter.isKnoraBuiltInProjectIriStr(checkedProjectIri)) {
      throw BadRequestException(s"Metadata cannot be updated for a built-in project")
    }

    for {
      projectInfoResponse: ProjectGetResponseADM <-
        appActor
          .ask(
            ProjectGetRequestADM(
              identifier = ProjectIdentifierADM(maybeIri = Some(checkedProjectIri)),
              requestingUser = requestingUser
            )
          )
          .mapTo[ProjectGetResponseADM]
    } yield projectInfoResponse.project
  }

  /**
   * Helper method converting an [[Either]] to a [[Future]].
   */
  def toFuture[A](either: Either[Throwable, A]): Future[A] = either.fold(Future.failed, Future.successful)

  /**
   * Helper method converting an [[zio.prelude.Validation]] to a [[scala.concurrent.Future]].
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
