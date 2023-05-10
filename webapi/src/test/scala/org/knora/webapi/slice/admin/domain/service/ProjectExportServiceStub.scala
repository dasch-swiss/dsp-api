/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Task
import zio.ULayer
import zio.ZLayer
import zio.nio.file
import zio.nio.file.Path

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
     * @return the [[Path]] to the file to which the project was exported
     */
    override def exportProjectTriples(project: KnoraProject): Task[Path] = ???

    /**
     * Exports a project to a file.
     * The file format is TriG.
     * The data exported is:
     * * the project metadata
     * * the project's permission data
     * * the triples of the project's ontologies
     *
     * @param project    the project to be exported
     * @param targetFile the file to which the project is to be exported
     * @return the [[Path]] to the file to which the project was exported
     */
    override def exportProjectTriples(project: KnoraProject, targetFile: Path): Task[Path] = ???

    override def exportProject(project: KnoraProject): Task[file.Path] = ???
  })
}
