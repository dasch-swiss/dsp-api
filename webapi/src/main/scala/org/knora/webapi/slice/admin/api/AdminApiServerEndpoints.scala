/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*
import zio.*

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

  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] =
    (filesServerEndpoints.serverEndpoints ++
      groupsServerEndpoints.serverEndpoints ++
      adminListsServerEndpoints.serverEndpoints ++
      maintenanceServerEndpoints.serverEndpoints ++
      permissionsServerEndpoints.serverEndpoints ++
      projectsLegalInfoServerEndpoints.serverEndpoints ++
      projectsServerEndpoints.serverEndpoints ++
      storeServerEndpoints.serverEndpoints ++
      usersServerEndpoints.serverEndpoints)
      .map(_.tag("Admin API"))
}

object AdminApiServerEndpoints {
  val layer = ZLayer.derive[AdminApiServerEndpoints]
}
