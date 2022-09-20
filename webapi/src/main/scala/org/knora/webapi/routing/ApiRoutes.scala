/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.http.version.ServerVersion
import org.knora.webapi.routing.AroundDirectives
import org.knora.webapi.routing.HealthRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RejectingRoute
import org.knora.webapi.routing.VersionRoute
import org.knora.webapi.routing.admin.FilesRouteADM
import org.knora.webapi.routing.admin.GroupsRouteADM
import org.knora.webapi.routing.admin.ListsRouteADM
import org.knora.webapi.routing.admin.PermissionsRouteADM
import org.knora.webapi.routing.admin.ProjectsRouteADM
import org.knora.webapi.routing.admin.StoreRouteADM
import org.knora.webapi.routing.admin.UsersRouteADM
import org.knora.webapi.routing.v1.AssetsRouteV1
import org.knora.webapi.routing.v1.AuthenticationRouteV1
import org.knora.webapi.routing.v1.CkanRouteV1
import org.knora.webapi.routing.v1.ListsRouteV1
import org.knora.webapi.routing.v1.ProjectsRouteV1
import org.knora.webapi.routing.v1.ResourceTypesRouteV1
import org.knora.webapi.routing.v1.ResourcesRouteV1
import org.knora.webapi.routing.v1.SearchRouteV1
import org.knora.webapi.routing.v1.StandoffRouteV1
import org.knora.webapi.routing.v1.UsersRouteV1
import org.knora.webapi.routing.v1.ValuesRouteV1
import org.knora.webapi.routing.v2.AuthenticationRouteV2
import org.knora.webapi.routing.v2.ListsRouteV2
import org.knora.webapi.routing.v2.OntologiesRouteV2
import org.knora.webapi.routing.v2.ResourcesRouteV2
import org.knora.webapi.routing.v2.SearchRouteV2
import org.knora.webapi.routing.v2.StandoffRouteV2
import org.knora.webapi.routing.v2.ValuesRouteV2

trait ApiRoutes {
  val routes: Route
}

object ApiRoutes {

  /**
   * All routes composed together.
   */
  val layer: ZLayer[ActorSystem & AppRouter & core.State & AppConfig, Nothing, ApiRoutes] =
    ZLayer {
      for {
        sys       <- ZIO.service[ActorSystem]
        router    <- ZIO.service[AppRouter]
        appConfig <- ZIO.service[AppConfig]
        routeData <- ZIO.succeed(
                       KnoraRouteData(
                         system = sys.system,
                         appActor = router.ref,
                         appConfig = appConfig
                       )
                     )
        runtime <- ZIO.runtime[core.State]
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
private final case class ApiRoutesImpl(routeData: KnoraRouteData, runtime: Runtime[core.State], appConfig: AppConfig)
    extends ApiRoutes
    with AroundDirectives {

  val routes =
    logDuration {
      ServerVersion.addServerHeader {
        DSPApiDirectives.handleErrors(routeData.system, appConfig) {
          CorsDirectives.cors(CorsSettings(routeData.system)) {
            DSPApiDirectives.handleErrors(routeData.system, appConfig) {
              new HealthRoute(routeData, runtime).makeRoute ~
                new VersionRoute().makeRoute ~
                new RejectingRoute(routeData, runtime).makeRoute ~
                new ResourcesRouteV1(routeData).makeRoute ~
                new ValuesRouteV1(routeData).makeRoute ~
                new StandoffRouteV1(routeData).makeRoute ~
                new ListsRouteV1(routeData).makeRoute ~
                new ResourceTypesRouteV1(routeData).makeRoute ~
                new SearchRouteV1(routeData).makeRoute ~
                new AuthenticationRouteV1(routeData).makeRoute ~
                new AssetsRouteV1(routeData).makeRoute ~
                new CkanRouteV1(routeData).makeRoute ~
                new UsersRouteV1(routeData).makeRoute ~
                new ProjectsRouteV1(routeData).makeRoute ~
                new OntologiesRouteV2(routeData).makeRoute ~
                new SearchRouteV2(routeData).makeRoute ~
                new ResourcesRouteV2(routeData).makeRoute ~
                new ValuesRouteV2(routeData).makeRoute ~
                new StandoffRouteV2(routeData).makeRoute ~
                new ListsRouteV2(routeData).makeRoute ~
                new AuthenticationRouteV2(routeData).makeRoute ~
                new GroupsRouteADM(routeData).makeRoute ~
                new ListsRouteADM(routeData).makeRoute ~
                new PermissionsRouteADM(routeData).makeRoute ~
                new ProjectsRouteADM(routeData).makeRoute ~
                new StoreRouteADM(routeData).makeRoute ~
                new UsersRouteADM(routeData).makeRoute ~
                new FilesRouteADM(routeData).makeRoute
            }
          }
        }
      }
    }

}
