/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core
import org.knora.webapi.core.ActorSystem
import org.knora.webapi.core.AppRouter
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.http.version.ServerVersion
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.routing
import org.knora.webapi.routing.admin._
import org.knora.webapi.routing.v1._
import org.knora.webapi.routing.v2._
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.resourceinfo.api.RestResourceInfoService

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
      with AppConfig
      with AppRouter
      with MessageRelay
      with RestCardinalityService
      with RestResourceInfoService
      with StringFormatter
      with core.State
      with routing.Authenticator,
    ApiRoutes
  ] =
    ZLayer {
      for {
        sys       <- ZIO.service[ActorSystem]
        router    <- ZIO.service[AppRouter]
        appConfig <- ZIO.service[AppConfig]
        routeData <- ZIO.succeed(KnoraRouteData(sys.system, router.ref, appConfig))
        runtime <- ZIO.runtime[
                     AppConfig
                       with MessageRelay
                       with RestCardinalityService
                       with RestResourceInfoService
                       with StringFormatter
                       with core.State
                       with routing.Authenticator
                   ]
      } yield ApiRoutesImpl(routeData, runtime, appConfig)
    }
}

/**
 * All routes composed together and CORS activated based on the
 * the configuration in application.conf (akka-http-cors).
 *
 * ALL requests go through each of the routes in ORDER.
 * The FIRST matching route is used for handling a request.
 */
private final case class ApiRoutesImpl(
  private val routeData: KnoraRouteData,
  private implicit val runtime: Runtime[
    AppConfig
      with MessageRelay
      with RestCardinalityService
      with RestResourceInfoService
      with StringFormatter
      with core.State
      with routing.Authenticator
  ],
  private val appConfig: AppConfig
) extends ApiRoutes
    with AroundDirectives {

  val routes =
    logDuration {
      ServerVersion.addServerHeader {
        DSPApiDirectives.handleErrors(routeData.system, appConfig) {
          CorsDirectives.cors(CorsSettings(routeData.system)) {
            DSPApiDirectives.handleErrors(routeData.system, appConfig) {
              HealthRoute(routeData, runtime).makeRoute ~
                VersionRoute().makeRoute ~
                RejectingRoute(routeData, runtime).makeRoute ~
                ResourcesRouteV1(routeData, runtime).makeRoute ~
                ValuesRouteV1().makeRoute ~
                StandoffRouteV1(routeData, runtime).makeRoute ~
                ListsRouteV1(routeData, runtime).makeRoute ~
                ResourceTypesRouteV1(routeData, runtime).makeRoute ~
                SearchRouteV1().makeRoute ~
                AuthenticationRouteV1(routeData, runtime).makeRoute ~
                AssetsRouteV1(routeData, runtime).makeRoute ~
                CkanRouteV1(routeData, runtime).makeRoute ~
                UsersRouteV1().makeRoute ~
                ProjectsRouteV1(routeData, runtime).makeRoute ~
                OntologiesRouteV2(routeData, runtime).makeRoute ~
                SearchRouteV2(routeData, runtime).makeRoute ~
                ResourcesRouteV2(routeData, runtime).makeRoute ~
                ValuesRouteV2(routeData, runtime).makeRoute ~
                StandoffRouteV2(routeData, runtime).makeRoute ~
                ListsRouteV2(routeData, runtime).makeRoute ~
                AuthenticationRouteV2(routeData, runtime).makeRoute ~
                GroupsRouteADM(routeData, runtime).makeRoute ~
                ListsRouteADM(routeData, runtime).makeRoute ~
                PermissionsRouteADM(routeData, runtime).makeRoute ~
                ProjectsRouteADM(routeData, runtime).makeRoute ~
                StoreRouteADM(routeData, runtime).makeRoute ~
                UsersRouteADM(routeData, runtime).makeRoute ~
                FilesRouteADM(routeData, runtime).makeRoute
            }
          }
        }
      }
    }

}
