/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.*

import sttp.tapir.ztapir.*

final case class AdminApiServerEndpoints(
  private val adminListsServerEndpoints: AdminListsServerEndpoints,
  private val filesServerEndpoints: FilesServerEndpoints,
  private val groupsServerEndpoints: GroupsServerEndpoints,
  private val maintenanceServerEndpoints: MaintenanceServerEndpoints,
  private val permissionsServerEndpoints: PermissionsServerEndpoints,
  private val projectsServerEndpoints: ProjectsServerEndpoints,
  private val projectsLegalInfoServerEndpoints: ProjectsLegalInfoServerEndpoints,
  private val storeServerEndpoints: StoreServerEndpoints,
  private val usersServerEndpoints: UsersServerEndpoints,
) {

  val serverEndpoints =
    filesServerEndpoints.serverEndpoints ++
      groupsServerEndpoints.serverEndpoints ++
      adminListsServerEndpoints.serverEndpoints ++
      maintenanceServerEndpoints.serverEndpoints ++
      permissionsServerEndpoints.serverEndpoints ++
      projectsLegalInfoServerEndpoints.serverEndpoints ++
      projectsServerEndpoints.serverEndpoints ++
      storeServerEndpoints.serverEndpoints ++
      usersServerEndpoints.serverEndpoints
}

object AdminApiServerEndpoints {
  val layer = ZLayer.derive[AdminApiServerEndpoints]
}
