/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routingz

import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core
import org.knora.webapi.core.ActorSystem
import org.knora.webapi.core.AppRouter
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.http.version.ServerVersion
import org.knora.webapi.routing.AroundDirectives
import org.knora.webapi.routing.KnoraRouteData
import zio._

trait ApiZRoutes {
  val routes: Route
}

object ApiZRoutes {

  /**
   * All routes composed together.
   */
  val layer: ZLayer[ActorSystem & AppRouter & core.State & AppConfig, Nothing, ApiZRoutes] =
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
      } yield ApiZRoutesImpl(routeData, runtime, appConfig)
    }
}

/**
 * All routes composed together and CORS activated based on the
 * the configuration in application.conf (akka-http-cors).
 *
 * ALL requests go through each of the routes in ORDER.
 * The FIRST matching route is used for handling a request.
 */
private final case class ApiZRoutesImpl(routeData: KnoraRouteData, runtime: Runtime[core.State], appConfig: AppConfig)
    extends ApiZRoutes
    with AroundDirectives {

  /**
   * Needed middleware for the following directives:
   * - logDuration
   * - ServerVersion.addServerHeader
   * - DSPApiDirectives.handleErrors
   * - CorsDirectives.cors(CorsSettings(routeData.system))
   */
  val routes =
    logDuration {
      ServerVersion.addServerHeader {
        DSPApiDirectives.handleErrors(routeData.system, appConfig) {
          CorsDirectives.cors(CorsSettings(routeData.system)) {
            DSPApiDirectives.handleErrors(routeData.system, appConfig) {
              HealthZRoute(routeData, runtime).makeRoute
            }
          }
        }
      }
    }

}
