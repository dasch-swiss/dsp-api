/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import cats.implicits.*
import zio.Chunk
import zio.NonEmptyChunk
import zio.Task

import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.DefaultObjectAccessPermissionPart
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.ForWhat
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess
import org.knora.webapi.slice.admin.repo.service.EntityWithId
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.repo.service.CrudRepository

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

    def groupOption: Option[GroupIri] = this match {
      case Group(iri) => Some(iri)
      case _          => None
    }
    def resourceClassOption: Option[InternalIri] = this match {
      case ResourceClass(iri)               => Some(iri)
      case ResourceClassAndProperty(iri, _) => Some(iri)
      case _                                => None
    }
    def propertyOption: Option[InternalIri] = this match {
      case Property(iri)                    => Some(iri)
      case ResourceClassAndProperty(_, iri) => Some(iri)
      case _                                => None
    }
  }
  object ForWhat {

    def apply(groupIri: GroupIri): ForWhat                                           = ForWhat.Group(groupIri)
    def apply(resourceClassIri: ResourceClassIri): ForWhat                           = ForWhat.ResourceClass(resourceClassIri.toInternalIri)
    def apply(propertyIri: PropertyIri): ForWhat                                     = ForWhat.Property(propertyIri.toInternalIri)
    def apply(resourceClassIri: ResourceClassIri, propertyIri: PropertyIri): ForWhat =
      ForWhat.ResourceClassAndProperty(resourceClassIri.toInternalIri, propertyIri.toInternalIri)

    def fromIris(
      group: Option[GroupIri],
      resourceClass: Option[ResourceClassIri],
      property: Option[PropertyIri],
    ): Either[String, ForWhat] =
      from(group, resourceClass.map(_.toInternalIri), property.map(_.toInternalIri))

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
        case _                                                   =>
          Left(
            s"DOAP restrictions must be either for a group, a resource class, a property, " +
              s"or a combination of a resource class and a property. ",
          )
      }
  }

  final case class DefaultObjectAccessPermissionPart(
    permission: Permission.ObjectAccess,
    groups: NonEmptyChunk[GroupIri],
  )
  object DefaultObjectAccessPermissionPart {
    def from(permissions: Seq[PermissionADM]): Either[String, Seq[DefaultObjectAccessPermissionPart]] =
      permissions.traverse(extractObjectAccessPermissionAndGroupIri).flatMap { permsAndGroups =>
        permsAndGroups.groupMap { case (perm, _) => perm } { case (_, group) => group }.toSeq.traverse(makePart)
      }

    private def extractObjectAccessPermissionAndGroupIri(adm: PermissionADM): Either[String, (ObjectAccess, GroupIri)] =
      for {
        permFromName <- Option.when(adm.name != "")(adm.name).traverse(ObjectAccess.fromToken)
        permFromCode <- adm.permissionCode.traverse(ObjectAccess.from)
        perm         <- (permFromName, permFromCode) match {
                  case (Some(p), None)      => Right(p)
                  case (None, Some(p))      => Right(p)
                  case (Some(p1), Some(p2)) =>
                    if (p1 == p2) Right(p1)
                    else
                      Left(
                        s"Given permission code '${adm.permissionCode.get}' and permission name '${adm.name}' are not consistent.",
                      )
                  case (None, None) =>
                    Left(s"Invalid permission token '', it should be one of ${ObjectAccess.allTokens.mkString(", ")}")
                }
        group <- adm.additionalInformation.toRight("No object access group present").flatMap(GroupIri.from)
      } yield (perm, group)

    private def makePart(perm: ObjectAccess, groups: Seq[GroupIri]): Either[String, DefaultObjectAccessPermissionPart] =
      NonEmptyChunk
        .fromIterableOption(groups)
        .toRight("No groups found for permission")
        .map(DefaultObjectAccessPermissionPart(perm, _))
  }

  def from(
    id: PermissionIri,
    forProject: ProjectIri,
    forWhat: ForWhat,
    perms: Set[PermissionADM],
  ): Either[String, DefaultObjectAccessPermission] =
    DefaultObjectAccessPermissionPart
      .from(perms.toSeq)
      .flatMap(NonEmptyChunk.fromIterableOption(_).toRight("No permissions found"))
      .map(DefaultObjectAccessPermission(id, forProject, forWhat, _))
}

trait DefaultObjectAccessPermissionRepo extends CrudRepository[DefaultObjectAccessPermission, PermissionIri] {

  def findByProject(projectIri: ProjectIri): Task[Chunk[DefaultObjectAccessPermission]]

  final def findByProject(project: KnoraProject): Task[Chunk[DefaultObjectAccessPermission]] = findByProject(project.id)

  def findByProjectAndForWhat(projectIri: ProjectIri, forWhat: ForWhat): Task[Option[DefaultObjectAccessPermission]]
}
