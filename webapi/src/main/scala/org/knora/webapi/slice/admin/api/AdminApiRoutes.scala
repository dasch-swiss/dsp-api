/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import org.apache.pekko.http.scaladsl.server.Route
import zio.ZLayer

import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

final case class AdminApiRoutes(
  private val filesEndpoints: FilesEndpointsHandler,
  private val groups: GroupsEndpointsHandler,
  private val adminLists: AdminListsEndpointsHandlers,
  private val maintenance: MaintenanceEndpointsHandlers,
  private val permissions: PermissionsEndpointsHandlers,
  private val project: ProjectsEndpointsHandler,
  private val projectLegalInfo: ProjectsLegalInfoEndpointsHandler,
  private val storeEndpoints: StoreEndpointsHandler,
  private val tapirToPekko: TapirToPekkoInterpreter,
  private val users: UsersEndpointsHandler,
) {

  private val handlers =
    filesEndpoints.allHandlers ++
      groups.allHandlers ++
      adminLists.allHandlers ++
      maintenance.allHandlers ++
      permissions.allHanders ++
      projectLegalInfo.allHandlers ++
      project.allHanders ++
      storeEndpoints.allHandlers ++
      users.allHanders

  val routes: Seq[Route] = handlers.map(tapirToPekko.toRoute(_))
}

object AdminApiRoutes {
  val layer = ZLayer.derive[AdminApiRoutes]
}
