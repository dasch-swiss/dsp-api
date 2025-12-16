/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.Chunk
import zio.Task

import scala.util.Try

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.service.EntityWithId
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.repo.service.CrudRepository

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
      if permission.isRestricted then Left(s"Permission $permission needs to be restricted with additional information")
      else Right(AdministrativePermissionPart.Simple(permission))
  }

  final case class ResourceCreateRestricted(resourceClassIris: Chunk[InternalIri])
      extends AdministrativePermissionPart {
    val permission: Permission.Administrative = Permission.Administrative.ProjectResourceCreateRestricted
  }
  object ResourceCreateRestricted {
    def apply(resourceClassIris: Seq[ResourceClassIri]): ResourceCreateRestricted =
      ResourceCreateRestricted(Chunk.fromIterable(resourceClassIris.map(_.toInternalIri)))
  }

  final case class ProjectAdminGroupRestricted(groupIris: Chunk[GroupIri]) extends AdministrativePermissionPart {
    val permission: Permission.Administrative = Permission.Administrative.ProjectResourceCreateRestricted
  }

  def from(adm: PermissionADM)(implicit sf: StringFormatter): Either[String, AdministrativePermissionPart] =
    Permission.Administrative.fromToken(adm.name).toRight(s"Invalid Permission name in $adm").flatMap { perm =>
      (perm, adm.additionalInformation) match {
        case (p, None) if perm.isSimple                                              => Right(Simple.unsafeFrom(p))
        case (Permission.Administrative.ProjectResourceCreateRestricted, Some(info)) =>
          for {
            smartIri      <- Try(info.toSmartIri).toEither.left.map(_.getMessage)
            resourceClass <- ResourceClassIri.from(smartIri)
          } yield ResourceCreateRestricted(Chunk(resourceClass))
        case (Permission.Administrative.ProjectAdminGroupRestricted, Some(info)) =>
          GroupIri.from(info).map(groupIri => ProjectAdminGroupRestricted(Chunk(groupIri)))
        case _ => Left(s"Invalid administrative permission data: $adm")
      }
    }
}

trait AdministrativePermissionRepo extends CrudRepository[AdministrativePermission, PermissionIri] {

  def findByGroupAndProject(groupIri: GroupIri, projectIri: ProjectIri): Task[Option[AdministrativePermission]]

  def findByProject(projectIri: ProjectIri): Task[Chunk[AdministrativePermission]]

  final def findByProject(project: KnoraProject): Task[Chunk[AdministrativePermission]] = findByProject(project.id)
}
