package org.knora.webapi.slice.admin.api

import sttp.tapir.AnyEndpoint
import zio.ZLayer

final case class AdminApiEndpoints(
  maintenanceEndpoints: MaintenanceEndpoints,
  permissionsEndpoints: PermissionsEndpoints,
  projectsEndpoints: ProjectsEndpoints,
  usersEndpoints: UsersEndpoints
) {

  val endpoints: Seq[AnyEndpoint] =
    maintenanceEndpoints.endpoints ++
      permissionsEndpoints.endpoints ++
      projectsEndpoints.endpoints ++
      usersEndpoints.endpoints
}

object AdminApiEndpoints {
  val layer = ZLayer.derive[AdminApiEndpoints]
}
