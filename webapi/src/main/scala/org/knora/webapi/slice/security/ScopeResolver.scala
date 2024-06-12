/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security

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
import org.knora.webapi.slice.infrastructure.Scope
import org.knora.webapi.slice.infrastructure.ScopeValue
import org.knora.webapi.slice.infrastructure.ScopeValue.Write

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
