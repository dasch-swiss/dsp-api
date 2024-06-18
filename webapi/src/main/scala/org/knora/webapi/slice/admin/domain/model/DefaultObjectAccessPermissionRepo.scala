/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.Chunk
import zio.NonEmptyChunk
import zio.Task

import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.DefaultObjectAccessPermissionPart
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.ForWhat
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.service.EntityWithId
import org.knora.webapi.slice.common.repo.service.CrudRepository
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

final case class DefaultObjectAccessPermission(
  id: PermissionIri,
  forProject: ProjectIri,
  forWhat: ForWhat,
  permission: Chunk[DefaultObjectAccessPermissionPart],
) extends EntityWithId[PermissionIri]

object DefaultObjectAccessPermission {
  enum ForWhat {
    case Group(iri: GroupIri)
    case ResourceClass(iri: InternalIri)
    case Property(iri: InternalIri)
    case ResourceClassAndProperty(resourceClass: InternalIri, property: InternalIri)
  }
  object ForWhat {
    def from(
      group: Option[GroupIri],
      resourceClass: Option[InternalIri],
      property: Option[InternalIri],
    ): Either[String, ForWhat] =
      (group, resourceClass, property) match {
        case (None, Some(rc: InternalIri), Some(p: InternalIri)) => Right(ResourceClassAndProperty(rc, p))
        case (None, None, Some(p: InternalIri))                  => Right(Property(p))
        case (None, Some(rc: InternalIri), None)                 => Right(ResourceClass(rc))
        case (Some(g: GroupIri), None, None)                     => Right(Group(g))
        case _                                                   => Left(s"Invalid combination of group $group resourceClass $resourceClass and property $property.")
      }
  }

  final case class DefaultObjectAccessPermissionPart(
    permission: Permission.ObjectAccess,
    groups: NonEmptyChunk[GroupIri],
  )
}

trait DefaultObjectAccessPermissionRepo extends CrudRepository[DefaultObjectAccessPermission, PermissionIri] {

  def findByProject(projectIri: ProjectIri): Task[Chunk[DefaultObjectAccessPermission]]

  final def findByProject(project: KnoraProject): Task[Chunk[DefaultObjectAccessPermission]] = findByProject(project.id)
}
