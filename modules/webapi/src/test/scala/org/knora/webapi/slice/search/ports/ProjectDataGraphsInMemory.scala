/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search.ports

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.common.domain.InternalIri

final case class ProjectDataGraphsInMemory(graphs: Map[ProjectIri, InternalIri] = Map.empty) extends ProjectDataGraphs {
  override def dataGraphOf(project: ProjectIri): Task[Option[InternalIri]] = ZIO.succeed(graphs.get(project))
}
