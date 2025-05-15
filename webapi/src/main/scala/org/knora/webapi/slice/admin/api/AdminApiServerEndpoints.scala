/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.ZServerEndpoint

final case class AdminApiServerEndpoints(
  files: FilesServerEndpoints,
  groups: GroupsServerEndpoints,
  lists: ListsServerEndpoints,
  maintenance: MaintenanceServerEndpoints,
  permissions: PermissionsServerEndpoints,
  projectLegalInfo: ProjectsLegalInfoServerEndpoints,
  projects: ProjectsServerEndpoints,
  storeEndpoints: StoreServerEndpoints,
  users: UsersServerEndpoints,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] =
    files.serverEndpoints ++
      groups.serverEndpoints ++
      lists.serverEndpoints ++
      maintenance.serverEndpoints ++
      permissions.serverEndpoints ++
      projectLegalInfo.serverEndpoints ++
      projects.serverEndpoints ++
      storeEndpoints.serverEndpoints ++
      users.serverEndpoints
}

object AdminApiServerEndpoints {
  val layer = ZLayer.derive[AdminApiServerEndpoints]
}
