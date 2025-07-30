/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.api

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer

import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

final case class ProjectsApiRoutes(
  projectsEndpointsHandler: ProjectsEndpointsHandler,
  tapirToPekko: TapirToPekkoInterpreter,
) {

  val routes: Route =
    // Remove path prefix since endpoint now includes full path for proper OpenAPI documentation
    concat(
      projectsEndpointsHandler.allHandlers.map(tapirToPekko.toRoute(_)): _*,
    )
}

object ProjectsApiRoutes {
  val layer = ZLayer.derive[ProjectsApiRoutes]
}
