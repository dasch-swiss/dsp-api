/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import zio.UIO
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.Permission.Administrative
import org.knora.webapi.slice.admin.domain.model.Permission.Administrative.ProjectAdminAll
import org.knora.webapi.slice.admin.domain.model.Permission.Administrative.ProjectResourceCreateAll
import org.knora.webapi.slice.admin.domain.model.Permission.Administrative.ProjectResourceCreateRestricted
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.infrastructure.ScopeValue.Write

final case class Scope(values: Set[ScopeValue]) { self =>
  def toScopeString: String         = self.values.map(_.toScopeString).mkString(" ")
  def +(addThis: ScopeValue): Scope = Scope(values.foldLeft(Set(addThis)) { case (s, old) => s.flatMap(_.merge(old)) })
}

object Scope {
  val empty: Scope = Scope(Set.empty)
  val admin: Scope = Scope(Set(ScopeValue.Admin))

  def from(scopeValues: Seq[ScopeValue]): Scope = scopeValues.foldLeft(Scope.empty)(_ + _)
  def read(project: Shortcode): Scope           = Scope(Set(ScopeValue.Read(project)))
  def write(project: Shortcode): Scope          = Scope(Set(ScopeValue.Write(project)))
}

sealed trait ScopeValue {
  def toScopeString: String
  final def merge(other: ScopeValue): Set[ScopeValue] = ScopeValue.merge(this, other)
}

object ScopeValue {
  final case class Read(project: Shortcode) extends ScopeValue {
    override def toScopeString: String = s"read:project:${project.value}"
  }

  final case class Write(project: Shortcode) extends ScopeValue {
    override def toScopeString: String = s"write:project:${project.value}"
  }

  case object Admin extends ScopeValue {
    override def toScopeString: String = "admin"
  }

  def merge(one: ScopeValue, two: ScopeValue): Set[ScopeValue] =
    (one, two) match {
      case (Admin, _) | (_, Admin)            => Set(Admin)
      case (Write(p1), Write(p2)) if p1 == p2 => Set(Write(p1))
      case (Write(p1), Read(p2)) if p1 == p2  => Set(Write(p1))
      case (Read(p1), Write(p2)) if p1 == p2  => Set(Write(p1))
      case (Read(p1), Read(p2)) if p1 == p2   => Set(Read(p1))
      case (a, b)                             => Set(a, b)
    }
}

final case class ScopeResolver(projectService: KnoraProjectService) {
  def resolve(user: User): UIO[Scope] =
    if (user.isSystemAdmin || user.isSystemUser) { ZIO.succeed(Scope.admin) }
    else { mapUserPermissionsToScope(user) }

  private def mapUserPermissionsToScope(user: User): UIO[Scope] =
    ZIO
      .foreach(user.permissions.administrativePermissionsPerProject.toSeq) { case (prjIri, permission) =>
        projectService
          .findById(ProjectIri.unsafeFrom(prjIri))
          .orDie
          .map(_.map(prj => mapPermissionToScope(permission, prj.shortcode)).getOrElse(Set.empty))
      }
      .map(scopeValues => Scope.from(scopeValues.flatten))

  private def mapPermissionToScope(permission: Set[PermissionADM], shortcode: Shortcode): Set[ScopeValue] =
    permission
      .map(_.name)
      .flatMap(Administrative.fromToken)
      .flatMap {
        case ProjectResourceCreateAll | ProjectResourceCreateRestricted | ProjectAdminAll => Some(Write(shortcode))
        case _                                                                            => None
      }
}
object ScopeResolver {
  val layer = ZLayer.derive[ScopeResolver]
}
