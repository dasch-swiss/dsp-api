/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.Chunk
import zio.Task

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.service.EntityWithId
import org.knora.webapi.slice.common.repo.service.CrudRepository

final case class DefaultObjectAccessPermission(
  id: PermissionIri,
  forProject: ProjectIri,
) extends EntityWithId[PermissionIri]

trait DefaultObjectAccessPermissionRepo extends CrudRepository[DefaultObjectAccessPermission, PermissionIri] {

  def findByProject(projectIri: ProjectIri): Task[Chunk[DefaultObjectAccessPermission]]

  final def findByProject(project: KnoraProject): Task[Chunk[DefaultObjectAccessPermission]] = findByProject(project.id)
}
