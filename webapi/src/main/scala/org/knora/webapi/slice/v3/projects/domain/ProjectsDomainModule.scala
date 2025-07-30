/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.domain

import zio.URLayer

import org.knora.webapi.slice.v3.projects.domain.model.ProjectsRepo
import org.knora.webapi.slice.v3.projects.domain.service.ProjectsService

object ProjectsDomainModule { self =>
  type Dependencies = ProjectsRepo

  type Provided = ProjectsService

  val layer: URLayer[self.Dependencies, self.Provided] =
    ProjectsService.layer
}
