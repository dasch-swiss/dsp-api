/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service
import zio.Chunk
import zio.Task
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermissionPart
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermissionRepo
import org.knora.webapi.slice.admin.domain.model.ForWhat
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri

final case class DefaultObjectAccessPermissionService(
  private val repo: DefaultObjectAccessPermissionRepo,
) {
  def findByProject(projectIri: ProjectIri): Task[Chunk[DefaultObjectAccessPermission]] =
    repo.findByProject(projectIri)

  def create(
    project: KnoraProject,
    forWhat: ForWhat,
    permission: Chunk[DefaultObjectAccessPermissionPart],
  ): Task[DefaultObjectAccessPermission] =
    repo.save(DefaultObjectAccessPermission(PermissionIri.makeNew(project.shortcode), project.id, forWhat, permission))
}

object DefaultObjectAccessPermissionService {
  val layer = ZLayer.derive[DefaultObjectAccessPermissionService]
}
