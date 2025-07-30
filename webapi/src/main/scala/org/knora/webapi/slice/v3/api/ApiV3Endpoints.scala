/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.api

import sttp.tapir.AnyEndpoint
import zio.ZLayer

import org.knora.webapi.slice.v3.projects.api.ProjectsEndpoints

final case class ApiV3Endpoints(
  private val projectsEndpoints: ProjectsEndpoints,
) {

  val endpoints: Seq[AnyEndpoint] =
    projectsEndpoints.endpoints
}

object ApiV3Endpoints {
  val layer = ZLayer.derive[ApiV3Endpoints]
}
