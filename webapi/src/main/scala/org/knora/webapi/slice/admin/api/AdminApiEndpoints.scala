/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.AnyEndpoint
import zio.ZLayer

final case class AdminApiEndpoints(
  groupsEndpoints: GroupsEndpoints,
  listsEndpoints: ListsEndpoints,
  maintenanceEndpoints: MaintenanceEndpoints,
  permissionsEndpoints: PermissionsEndpoints,
  projectsEndpoints: ProjectsEndpoints,
  projectsLegalInfoEndpoint: ProjectsLegalInfoEndpoints,
  storeEndpoints: StoreEndpoints,
  usersEndpoints: UsersEndpoints,
  filesEndpoints: FilesEndpoints,
) {

  val endpoints: Seq[AnyEndpoint] =
    groupsEndpoints.endpoints ++
      listsEndpoints.endpoints ++
      maintenanceEndpoints.endpoints ++
      permissionsEndpoints.endpoints ++
      projectsLegalInfoEndpoint.endpoints ++
      projectsEndpoints.endpoints ++
      storeEndpoints.endpoints ++
      usersEndpoints.endpoints ++
      filesEndpoints.endpoints
}

object AdminApiEndpoints {
  val layer = ZLayer.derive[AdminApiEndpoints]
}
