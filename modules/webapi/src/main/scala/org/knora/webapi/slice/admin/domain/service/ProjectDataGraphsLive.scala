/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.search.ports.ProjectDataGraphs

/** Serves a project's data graph to search, derived the one way the admin context derives it. */
final case class ProjectDataGraphsLive(projectService: KnoraProjectService) extends ProjectDataGraphs {

  override def dataGraphOf(project: ProjectIri): Task[Option[InternalIri]] =
    projectService.findById(project).map(_.map(projectService.getDataGraphForProject))
}

object ProjectDataGraphsLive {
  val layer = ZLayer.derive[ProjectDataGraphsLive]
}
