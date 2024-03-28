/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

sealed trait AdministrativePermission {
  def token: String
}

object AdministrativePermission {
  case object ProjectResourceCreateAll extends AdministrativePermission {
    override val token: String = "ProjectResourceCreateAllPermission"
  }
  case object ProjectResourceCreateRestricted extends AdministrativePermission {
    override val token: String = "ProjectResourceCreateRestrictedPermission"
  }
  case object ProjectAdminAll extends AdministrativePermission {
    override val token: String = "ProjectAdminAllPermission"
  }
  case object ProjectAdminGroupAll extends AdministrativePermission {
    override val token: String = "ProjectAdminGroupAllPermission"
  }
  case object ProjectAdminGroupRestricted extends AdministrativePermission {
    override val token: String = "ProjectAdminGroupRestrictedPermission"
  }
  case object ProjectAdminRightsAll extends AdministrativePermission {
    override val token: String = "ProjectAdminRightsAllPermission"
  }

  def fromToken(token: String): Option[AdministrativePermission] =
    AdministrativePermissions.all.find(_.token == token)
}

object AdministrativePermissions {
  val all: Set[AdministrativePermission] = Set(
    AdministrativePermission.ProjectResourceCreateAll,
    AdministrativePermission.ProjectResourceCreateRestricted,
    AdministrativePermission.ProjectAdminAll,
    AdministrativePermission.ProjectAdminGroupAll,
    AdministrativePermission.ProjectAdminGroupRestricted,
    AdministrativePermission.ProjectAdminRightsAll,
  )
  val allTokens: Set[String] = all.map(_.token)
}
