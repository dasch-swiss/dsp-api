/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Task
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.AdministrativePermission
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionRepo
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

final case class AdministrativePermissionService(repo: AdministrativePermissionRepo) {
  def findByGroupAndProject(groupIri: GroupIri, projectIri: ProjectIri): Task[Option[AdministrativePermission]] =
    repo.findByGroupAndProject(groupIri, projectIri)
}

object AdministrativePermissionService {
  val layer = ZLayer.derive[AdministrativePermissionService]
}
