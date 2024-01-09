/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.AnyEndpoint
import zio.ZLayer

final case class AdminApiEndpoints(
  groupsEndpoints: GroupsEndpoints,
  maintenanceEndpoints: MaintenanceEndpoints,
  permissionsEndpoints: PermissionsEndpoints,
  projectsEndpoints: ProjectsEndpoints,
  usersEndpoints: UsersEndpoints
) {

  val endpoints: Seq[AnyEndpoint] =
    groupsEndpoints.endpoints ++
      maintenanceEndpoints.endpoints ++
      permissionsEndpoints.endpoints ++
      projectsEndpoints.endpoints ++
      usersEndpoints.endpoints
}

object AdminApiEndpoints {
  val layer = ZLayer.derive[AdminApiEndpoints]
}
