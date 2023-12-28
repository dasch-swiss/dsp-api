/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.routing
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.admin.permissions.*

/**
 * Provides an pekko-http-routing function for API routes that deal with permissions.
 */
final case class PermissionsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[routing.Authenticator & StringFormatter & MessageRelay]
) extends KnoraRoute(routeData, runtime) {
  private val createPermissionRoute: CreatePermissionRouteADM = CreatePermissionRouteADM(routeData, runtime)
  private val getPermissionRoute: GetPermissionsRouteADM      = GetPermissionsRouteADM(routeData, runtime)
  private val updatePermissionRoute: UpdatePermissionRouteADM = UpdatePermissionRouteADM(routeData, runtime)

  override def makeRoute: Route =
    createPermissionRoute.makeRoute ~
      getPermissionRoute.makeRoute ~
      updatePermissionRoute.makeRoute
}
