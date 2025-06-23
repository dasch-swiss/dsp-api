/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.cors.scaladsl.CorsDirectives
import org.apache.pekko.http.cors.scaladsl.settings.CorsSettings
import org.apache.pekko.http.scaladsl.model.HttpMethods.*
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import org.knora.webapi.http.version.ServerVersion
import org.knora.webapi.routing
import org.knora.webapi.slice.admin.api.AdminApiRoutes
import org.knora.webapi.slice.infrastructure.api.ManagementRoutes
import org.knora.webapi.slice.lists.api.ListsApiV2Routes
import org.knora.webapi.slice.ontology.api.OntologiesApiRoutes
import org.knora.webapi.slice.resources.api.ResourceInfoRoutes
import org.knora.webapi.slice.resources.api.ResourcesApiRoutes
import org.knora.webapi.slice.search.api.SearchApiRoutes
import org.knora.webapi.slice.security.api.AuthenticationApiRoutes
import org.knora.webapi.slice.shacl.api.ShaclApiRoutes

/**
 * All routes composed together and CORS activated based on the
 * the configuration in application.conf (pekko-http-cors).
 *
 * ALL requests go through each of the routes in ORDER.
 * The FIRST matching route is used for handling a request.
 */
final case class ApiRoutes(
  adminApiRoutes: AdminApiRoutes,
  authenticationApiRoutes: AuthenticationApiRoutes,
  listsApiV2Routes: ListsApiV2Routes,
  resourceInfoRoutes: ResourceInfoRoutes,
  resourcesApiRoutes: ResourcesApiRoutes,
  searchApiRoutes: SearchApiRoutes,
  shaclApiRoutes: ShaclApiRoutes,
  managementRoutes: ManagementRoutes,
  ontologiesRoutes: OntologiesApiRoutes,
  system: ActorSystem,
) {
  val routes: Route =
    ServerVersion.addServerHeader {
      CorsDirectives.cors(
        CorsSettings(system).withAllowedMethods(List(GET, PUT, POST, DELETE, PATCH, HEAD, OPTIONS)),
      ) {
        (adminApiRoutes.routes ++
          authenticationApiRoutes.routes ++
          listsApiV2Routes.routes ++
          managementRoutes.routes ++
          ontologiesRoutes.routes ++
          resourceInfoRoutes.routes ++
          resourcesApiRoutes.routes ++
          searchApiRoutes.routes ++
          shaclApiRoutes.routes).reduce(_ ~ _)
      }
    }
}
object ApiRoutes {
  val layer = ZLayer.derive[ApiRoutes]
}
