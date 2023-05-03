/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Task
import zio.ULayer
import zio.ZIO
import zio.ZLayer

import java.nio.file.Path

import org.knora.webapi.slice.admin.domain.model.KnoraProject

object ProjectExportServiceStub {
  val layer: ULayer[ProjectExportService] = ZLayer.succeed(new ProjectExportService {

    /**
     * Exports a project to a file.
     * The file format is TriG.
     * The data exported is:
     * * the project metadata
     * * the project's permission data
     * * the triples of the project's ontologies
     *
     * @param project the project to be exported
     * @param file    the path to the file to which the project should be exported
     * @return the [[Path]] to the file to which the project was exported
     */
    override def exportProjectTriples(project: KnoraProject, file: Path): Task[Path] =
      ZIO.die(new UnsupportedOperationException("Not implemented"))
  })
}
