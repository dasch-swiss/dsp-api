/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Chunk
import zio.Task
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.AdministrativePermission
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionPart
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionRepo
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri

final case class AdministrativePermissionService(repo: AdministrativePermissionRepo) {
  def findByGroupAndProject(groupIri: GroupIri, projectIri: ProjectIri): Task[Option[AdministrativePermission]] =
    repo.findByGroupAndProject(groupIri, projectIri)

  def create(
    project: KnoraProject,
    group: KnoraGroup,
    permissions: Chunk[AdministrativePermissionPart],
  ): Task[AdministrativePermission] =
    repo.save(
      AdministrativePermission(
        PermissionIri.makeNew(project.shortcode),
        group.id,
        project.id,
        permissions,
      ),
    )
}

object AdministrativePermissionService {
  val layer = ZLayer.derive[AdministrativePermissionService]
}
