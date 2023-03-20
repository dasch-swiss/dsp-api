/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import org.knora.webapi.routing
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.admin.permissions._

/**
 * Provides an akka-http-routing function for API routes that deal with permissions.
 */
final case class PermissionsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: zio.Runtime[routing.Authenticator]
) extends KnoraRoute(routeData, runtime) {
  private val createPermissionRoute: CreatePermissionRouteADM = CreatePermissionRouteADM(routeData, runtime)
  private val getPermissionRoute: GetPermissionsRouteADM      = GetPermissionsRouteADM(routeData, runtime)
  private val updatePermissionRoute: UpdatePermissionRouteADM = UpdatePermissionRouteADM(routeData, runtime)
  private val deletePermissionRoute: DeletePermissionRouteADM = DeletePermissionRouteADM(routeData, runtime)

  override def makeRoute: Route =
    createPermissionRoute.makeRoute ~
      getPermissionRoute.makeRoute ~
      updatePermissionRoute.makeRoute ~
      deletePermissionRoute.makeRoute
}
