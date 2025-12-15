/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*
import zio.*

final class AdminApiServerEndpoints(
  adminListsServerEndpoints: AdminListsServerEndpoints,
  filesServerEndpoints: FilesServerEndpoints,
  groupsServerEndpoints: GroupsServerEndpoints,
  maintenanceServerEndpoints: MaintenanceServerEndpoints,
  permissionsServerEndpoints: PermissionsServerEndpoints,
  projectsServerEndpoints: ProjectsServerEndpoints,
  projectsLegalInfoServerEndpoints: ProjectsLegalInfoServerEndpoints,
  storeServerEndpoints: StoreServerEndpoints,
  usersServerEndpoints: UsersServerEndpoints,
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
