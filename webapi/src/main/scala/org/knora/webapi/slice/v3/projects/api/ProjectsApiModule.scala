/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.api

import zio.*

import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import org.knora.webapi.slice.v3.projects.api.service.ProjectsRestService
import org.knora.webapi.slice.v3.projects.domain.service.ProjectsService

object ProjectsApiModule { self =>
  type Dependencies =
      // format: off
      BaseEndpoints &
      HandlerMapper &
      ProjectsService &
      TapirToPekkoInterpreter
      // format: on

  type Provided = ProjectsApiRoutes & ProjectsEndpoints

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      ProjectsEndpoints.layer,
      ProjectsRestService.layer,
      ProjectsEndpointsHandler.layer,
      ProjectsApiRoutes.layer,
    )
}
