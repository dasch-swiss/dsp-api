/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer

import org.knora.webapi.slice.v3.projects.api.ProjectsApiRoutes

final case class V3Routes(
  projectsApiRoutes: ProjectsApiRoutes,
) {

  val routes: Route =
    // Remove v3 prefix since endpoints now include full path for proper OpenAPI documentation
    projectsApiRoutes.routes
}

object V3Routes {
  val layer = ZLayer.derive[V3Routes]
}
