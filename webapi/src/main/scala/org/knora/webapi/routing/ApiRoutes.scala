/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.cors.scaladsl.CorsDirectives
import org.apache.pekko.http.cors.scaladsl.settings.CorsSettings
import org.apache.pekko.http.scaladsl.model.HttpMethods._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core
import org.knora.webapi.core.AppRouter
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.http.version.ServerVersion
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.routing
import org.knora.webapi.routing.v2._
import org.knora.webapi.slice.admin.api.AdminApiRoutes
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.infrastructure.api.ManagementRoutes
import org.knora.webapi.slice.ontology.api.service.RestCardinalityService
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoutes
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.slice.search.api.SearchApiRoutes
import org.knora.webapi.store.iiif.api.SipiService

/**
 * All routes composed together and CORS activated based on the
 * the configuration in application.conf (pekko-http-cors).
 *
 * ALL requests go through each of the routes in ORDER.
 * The FIRST matching route is used for handling a request.
 */
final case class ApiRoutes(
  routeData: KnoraRouteData,
  adminApiRoutes: AdminApiRoutes,
  resourceInfoRoutes: ResourceInfoRoutes,
  searchApiRoutes: SearchApiRoutes,
  managementRoutes: ManagementRoutes,
  appConfig: AppConfig,
  implicit val runtime: Runtime[ApiRoutes.ApiRoutesRuntime],
) extends AroundDirectives {

  private implicit val system: ActorSystem = routeData.system

  val routes: Route =
    logDuration {
      ServerVersion.addServerHeader {
        DSPApiDirectives.handleErrors(appConfig) {
          CorsDirectives.cors(
            CorsSettings(routeData.system)
              .withAllowedMethods(List(GET, PUT, POST, DELETE, PATCH, HEAD, OPTIONS)),
          ) {
            DSPApiDirectives.handleErrors(appConfig) {
              (adminApiRoutes.routes ++ resourceInfoRoutes.routes ++ searchApiRoutes.routes ++ managementRoutes.routes)
                .reduce(_ ~ _) ~
                AuthenticationRouteV2().makeRoute ~
                ListsRouteV2().makeRoute ~
                OntologiesRouteV2().makeRoute ~
                ResourcesRouteV2(appConfig).makeRoute ~
                StandoffRouteV2().makeRoute ~
                ValuesRouteV2().makeRoute
            }
          }
        }
      }
    }
}

object ApiRoutes {

  private type ApiRoutesRuntime =
    AppConfig & AuthorizationRestService & core.State & IriConverter & MessageRelay & ProjectService &
      RestCardinalityService & routing.Authenticator & SearchApiRoutes & SearchResponderV2 & SipiService &
      StringFormatter & UserService & ValuesResponderV2

  /**
   * All routes composed together.
   */
  val layer: URLayer[
    ApiRoutesRuntime & ActorSystem & AdminApiRoutes & AppRouter & ManagementRoutes & ResourceInfoRoutes,
    ApiRoutes,
  ] =
    ZLayer {
      for {
        sys                <- ZIO.service[ActorSystem]
        router             <- ZIO.service[AppRouter]
        appConfig          <- ZIO.service[AppConfig]
        adminApiRoutes     <- ZIO.service[AdminApiRoutes]
        resourceInfoRoutes <- ZIO.service[ResourceInfoRoutes]
        searchApiRoutes    <- ZIO.service[SearchApiRoutes]
        managementRoutes   <- ZIO.service[ManagementRoutes]
        routeData          <- ZIO.succeed(KnoraRouteData(sys, router.ref, appConfig))
        runtime            <- ZIO.runtime[ApiRoutesRuntime]
      } yield ApiRoutes(
        routeData,
        adminApiRoutes,
        resourceInfoRoutes,
        searchApiRoutes,
        managementRoutes,
        appConfig,
        runtime,
      )
    }
}
