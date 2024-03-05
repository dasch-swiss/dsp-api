/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer

import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

final case class AdminApiRoutes(
  filesEndpoints: FilesEndpointsHandler,
  groups: GroupsEndpointsHandler,
  lists: ListsEndpointsHandlers,
  maintenance: MaintenanceEndpointsHandlers,
  permissions: PermissionsEndpointsHandlers,
  project: ProjectsEndpointsHandler,
  storeEndpoints: StoreEndpointsHandler,
  users: UsersEndpointsHandler,
  tapirToPekko: TapirToPekkoInterpreter,
) {

  private val handlers =
    filesEndpoints.allHandlers ++
      groups.allHandlers ++
      lists.allHandlers ++
      maintenance.allHandlers ++
      permissions.allHanders ++
      project.allHanders ++
      storeEndpoints.allHandlers ++
      users.allHanders

  val routes: Seq[Route] = handlers.map(tapirToPekko.toRoute(_))
}

object AdminApiRoutes {
  val layer = ZLayer.derive[AdminApiRoutes]
}
