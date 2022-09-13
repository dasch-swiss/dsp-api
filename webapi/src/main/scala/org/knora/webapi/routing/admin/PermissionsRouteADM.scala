/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import org.knora.webapi.config.AppConfig
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.admin.permissions._

/**
 * Provides an akka-http-routing function for API routes that deal with permissions.
 */
class PermissionsRouteADM(routeData: KnoraRouteData, appConfig: AppConfig) extends KnoraRoute(routeData, appConfig) {
  private val createPermissionRoute: CreatePermissionRouteADM = new CreatePermissionRouteADM(routeData, appConfig)
  private val getPermissionRoute: GetPermissionsRouteADM      = new GetPermissionsRouteADM(routeData, appConfig)
  private val updatePermissionRoute: UpdatePermissionRouteADM = new UpdatePermissionRouteADM(routeData, appConfig)
  private val deletePermissionRoute: DeletePermissionRouteADM = new DeletePermissionRouteADM(routeData, appConfig)

  override def makeRoute: Route =
    createPermissionRoute.makeRoute ~
      getPermissionRoute.makeRoute ~
      updatePermissionRoute.makeRoute ~
      deletePermissionRoute.makeRoute
}
