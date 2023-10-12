/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import org.apache.pekko
import org.apache.pekko.http.cors.scaladsl.CorsDirectives
import org.apache.pekko.http.scaladsl.model.HttpMethods.DELETE
import org.apache.pekko.http.scaladsl.model.HttpMethods.GET
import org.apache.pekko.http.scaladsl.model.HttpMethods.HEAD
import org.apache.pekko.http.scaladsl.model.HttpMethods.OPTIONS
import org.apache.pekko.http.scaladsl.model.HttpMethods.PATCH
import org.apache.pekko.http.scaladsl.model.HttpMethods.POST
import org.apache.pekko.http.scaladsl.model.HttpMethods.PUT
import zio._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core
import org.knora.webapi.core.ActorSystem
import org.knora.webapi.core.AppRouter
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.http.version.ServerVersion
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.routing
import org.knora.webapi.routing.admin._
import org.knora.webapi.routing.v2._
import org.knora.webapi.slice.admin.api.ProjectsEndpointsHandler
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoService
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import pekko.actor
import pekko.http.scaladsl.server.Directives._
import pekko.http.scaladsl.server.Route
import pekko.http.cors.scaladsl.settings.CorsSettings

import scala.concurrent.ExecutionContextExecutor

trait ApiRoutes {
  val routes: Route
}

object ApiRoutes {

  /**
   * All routes composed together.
   */
  val layer: URLayer[
    ActorSystem
      with AppConfig
      with AppRouter
      with IriConverter
      with KnoraProjectRepo
      with MessageRelay
      with ProjectADMRestService
      with ProjectsEndpointsHandler
      with RestCardinalityService
      with RestResourceInfoService
      with StringFormatter
      with ValuesResponderV2
      with core.State
      with routing.Authenticator,
    ApiRoutes
  ] =
    ZLayer {
      for {
        sys              <- ZIO.service[ActorSystem]
        router           <- ZIO.service[AppRouter]
        appConfig        <- ZIO.service[AppConfig]
        projectsHandler  <- ZIO.service[ProjectsEndpointsHandler]
        routeData        <- ZIO.succeed(KnoraRouteData(sys.system, router.ref, appConfig))
        tapirToPekkoRoute = TapirToPekkoInterpreter()(sys.system.dispatcher)
        runtime <- ZIO.runtime[
                     AppConfig
                       with IriConverter
                       with KnoraProjectRepo
                       with MessageRelay
                       with ProjectADMRestService
                       with RestCardinalityService
                       with RestResourceInfoService
                       with StringFormatter
                       with ValuesResponderV2
                       with core.State
                       with routing.Authenticator
                   ]
      } yield ApiRoutesImpl(routeData, projectsHandler, tapirToPekkoRoute, appConfig, runtime)
    }
}

/**
 * All routes composed together and CORS activated based on the
 * the configuration in application.conf (pekko-http-cors).
 *
 * ALL requests go through each of the routes in ORDER.
 * The FIRST matching route is used for handling a request.
 */
private final case class ApiRoutesImpl(
  routeData: KnoraRouteData,
  projectsHandler: ProjectsEndpointsHandler,
  tapirToPekkoRoute: TapirToPekkoInterpreter,
  appConfig: AppConfig,
  implicit val runtime: Runtime[
    AppConfig
      with IriConverter
      with KnoraProjectRepo
      with MessageRelay
      with ProjectADMRestService
      with RestCardinalityService
      with RestResourceInfoService
      with StringFormatter
      with ValuesResponderV2
      with core.State
      with routing.Authenticator
  ]
) extends ApiRoutes
    with AroundDirectives {

  implicit val system: actor.ActorSystem                  = routeData.system
  implicit val executionContext: ExecutionContextExecutor = routeData.system.dispatcher

  val routes: Route =
    logDuration {
      ServerVersion.addServerHeader {
        DSPApiDirectives.handleErrors(appConfig) {
          CorsDirectives.cors(
            CorsSettings(routeData.system)
              .withAllowedMethods(List(GET, PUT, POST, DELETE, PATCH, HEAD, OPTIONS))
          ) {
            DSPApiDirectives.handleErrors(appConfig) {
              val adminProjectsRoutes = projectsHandler.allHanders.map(tapirToPekkoRoute.toRoute(_)).reduce(_ ~ _)
              adminProjectsRoutes ~
                AuthenticationRouteV2().makeRoute ~
                FilesRouteADM(routeData, runtime).makeRoute ~
                GroupsRouteADM(routeData, runtime).makeRoute ~
                HealthRoute().makeRoute ~
                ListsRouteADM(routeData, runtime).makeRoute ~
                ListsRouteV2().makeRoute ~
                OntologiesRouteV2().makeRoute ~
                PermissionsRouteADM(routeData, runtime).makeRoute ~
                RejectingRoute(appConfig, runtime).makeRoute ~
                ResourcesRouteV2(appConfig).makeRoute ~
                SearchRouteV2(appConfig.v2.fulltextSearch.searchValueMinLength).makeRoute ~
                StandoffRouteV2().makeRoute ~
                StoreRouteADM(routeData, runtime).makeRoute ~
                UsersRouteADM().makeRoute ~
                ValuesRouteV2().makeRoute ~
                VersionRoute().makeRoute
            }
          }
        }
      }
    }
}
