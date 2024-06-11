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
import org.knora.webapi.slice.common.repo.service.Repository
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

final case class AdministrativePermission(
  id: PermissionIri,
  forGroup: GroupIri,
  forProject: ProjectIri,
  permissions: Chunk[AdministrativePermissionPart],
) extends EntityWithId[PermissionIri]

sealed trait AdministrativePermissionPart {
  def permission: Permission.Administrative
}

object AdministrativePermissionPart {

  final case class Simple private (permission: Permission.Administrative) extends AdministrativePermissionPart

  object Simple {

    def unsafeFrom(permission: Permission.Administrative): Simple =
      from(permission).fold(e => throw new IllegalArgumentException(e), identity)

    def from(permission: Permission.Administrative): Either[String, Simple] =
      if (restrictedPermissions.contains(permission)) { Left(s"Permission $permission needs to be restricted") }
      else { Right(AdministrativePermissionPart.Simple.apply(permission)) }
  }

  final case class ResourceCreateRestricted(resourceClassIris: Chunk[InternalIri])
      extends AdministrativePermissionPart {
    val permission: Permission.Administrative = Permission.Administrative.ProjectResourceCreateRestricted
  }

  final case class ProjectAdminGroupRestricted(groupIris: Chunk[GroupIri]) extends AdministrativePermissionPart {
    val permission: Permission.Administrative = Permission.Administrative.ProjectResourceCreateRestricted
  }

  private def restrictedPermissions = List(
    Permission.Administrative.ProjectResourceCreateRestricted,
    Permission.Administrative.ProjectAdminGroupRestricted,
  )
}

trait AdministrativePermissionRepo extends CrudRepository[AdministrativePermission, PermissionIri] {

  def findByGroupAndProject(groupIri: GroupIri, projectIri: ProjectIri): Task[Option[AdministrativePermission]]

  def findByProject(projectIri: ProjectIri): Task[Chunk[AdministrativePermission]]

  final def findByProject(project: KnoraProject): Task[Chunk[AdministrativePermission]] = findByProject(project.id)
}
