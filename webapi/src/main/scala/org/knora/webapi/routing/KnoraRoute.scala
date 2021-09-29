/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.routing

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.server.{RequestContext, Route, RouteResult}
import akka.pattern._
import akka.stream.Materializer
import akka.util.Timeout
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.{
  FeatureFactoryConfig,
  KnoraSettingsFeatureFactoryConfig,
  RequestContextFeatureFactoryConfig
}
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.{
  ProjectADM,
  ProjectGetRequestADM,
  ProjectGetResponseADM,
  ProjectIdentifierADM
}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Data needed to be passed to each route.
 *
 * @param system   the actor system.
 * @param appActor the main application actor.
 */
case class KnoraRouteData(system: ActorSystem, appActor: ActorRef)

/**
 * An abstract class providing functionality that is commonly used by Knora routes and by
 * feature factories that construct Knora routes.
 *
 * @param routeData a [[KnoraRouteData]] providing access to the application.
 */
abstract class KnoraRouteFactory(routeData: KnoraRouteData) {
  implicit protected val system: ActorSystem = routeData.system
  implicit protected val settings: KnoraSettingsImpl = KnoraSettings(system)
  implicit protected val timeout: Timeout = settings.defaultTimeout
  implicit protected val executionContext: ExecutionContext =
    system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
  implicit protected val materializer: Materializer = Materializer.matFromSystem(system)
  implicit protected val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  protected val applicationActor: ActorRef = routeData.appActor
  implicit protected val responderManager: ActorRef = routeData.appActor
  protected val storeManager: ActorRef = routeData.appActor
  protected val log: LoggingAdapter = akka.event.Logging(system, this.getClass)
  protected val baseApiUrl: String = settings.internalKnoraApiBaseUrl

  /**
   * Constructs a route. This can be done:
   *
   * - by statically returning a routing function (if this is an ordinary route that
   * doesn't use a feature factory, or if this is a route feature returned by
   * a feature factory)
   *
   * - by asking a feature factory for a routing function (if this is a façade route)
   *
   * - by making a choice based on a feature toggle (if this is a feature factory)
   *
   * @param featureFactoryConfig the per-request feature factory configuration.
   * @return a route configured with the features enabled by the feature factory configuration.
   */
  def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route
}

/**
 * An abstract class providing functionality that is commonly used in implementing Knora routes.
 *
 * @param routeData a [[KnoraRouteData]] providing access to the application.
 */
abstract class KnoraRoute(routeData: KnoraRouteData) extends KnoraRouteFactory(routeData) {

  /**
   * A [[KnoraSettingsFeatureFactoryConfig]] to use as the parent [[FeatureFactoryConfig]].
   */
  private val knoraSettingsFeatureFactoryConfig: KnoraSettingsFeatureFactoryConfig =
    new KnoraSettingsFeatureFactoryConfig(settings)

  /**
   * Returns a routing function that uses per-request feature factory configuration.
   */
  def knoraApiPath: Route = runRoute

  /**
   * A routing function that calls `makeRoute`, passing it the per-request feature factory configuration,
   * and runs the resulting routing function.
   *
   * @param requestContext the HTTP request context.
   * @return the result of running the route.
   */
  private def runRoute(requestContext: RequestContext): Future[RouteResult] = {
    // Construct the per-request feature factory configuration.
    val featureFactoryConfig: FeatureFactoryConfig = new RequestContextFeatureFactoryConfig(
      requestContext = requestContext,
      parent = knoraSettingsFeatureFactoryConfig
    )

    // Construct a routing function using that configuration.
    val route: Route = makeRoute(featureFactoryConfig)

    // Call the routing function.
    route(requestContext)
  }

  /**
   * Gets a [[ProjectADM]] corresponding to the specified project IRI.
   *
   * @param projectIri           the project IRI.
   * @param featureFactoryConfig the feature factory configuration.
   * @param requestingUser       the user making the request.
   * @return the corresponding [[ProjectADM]].
   */
  protected def getProjectADM(
    projectIri: IRI,
    featureFactoryConfig: FeatureFactoryConfig,
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
      projectInfoResponse: ProjectGetResponseADM <- (responderManager ? ProjectGetRequestADM(
        identifier = ProjectIdentifierADM(maybeIri = Some(checkedProjectIri)),
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )).mapTo[ProjectGetResponseADM]
    } yield projectInfoResponse.project
  }
}
