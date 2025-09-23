/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import sttp.tapir.ztapir.ZServerEndpoint

final case class AdminApiServerEndpoints(
  private val adminLists: AdminListsEndpointsHandlers,
  private val filesEndpoints: FilesEndpointsHandler,
  private val groups: GroupsEndpointsHandler,
  private val maintenance: MaintenanceEndpointsHandlers,
  private val permissions: PermissionsEndpointsHandlers,
  private val project: ProjectsEndpointsHandler,
  private val projectLegalInfo: ProjectsLegalInfoEndpointsHandler,
  private val storeEndpoints: StoreEndpointsHandler,
  private val users: UsersEndpointsHandler,
) {

  private val endpoints: List[ZServerEndpoint[Any, Any]] =
    filesEndpoints.allHandlers ++
      groups.allHandlers ++
      adminLists.allHandlers ++
      maintenance.allHandlers ++
      permissions.allHanders ++
      projectLegalInfo.allHandlers ++
      project.allHanders ++
      storeEndpoints.allHandlers ++
      users.allHanders
}

object AdminApiServerEndpoints {
  val layer = ZLayer.derive[AdminApiServerEndpoints]
}
