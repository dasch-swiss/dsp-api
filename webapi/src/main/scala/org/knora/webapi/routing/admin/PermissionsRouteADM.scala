/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.routing.admin.permissions._
import org.knora.webapi.routing.{KnoraRoute, KnoraRouteData}

/**
 * Provides an akka-http-routing function for API routes that deal with permissions.
 */
class PermissionsRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) {
  private val createPermissionRoute: CreatePermissionRouteADM = new CreatePermissionRouteADM(routeData)
  private val getPermissionRoute: GetPermissionsRouteADM      = new GetPermissionsRouteADM(routeData)
  private val updatePermissionRoute: UpdatePermissionRouteADM = new UpdatePermissionRouteADM(routeData)
  private val deletePermissionRoute: DeletePermissionRouteADM = new DeletePermissionRouteADM(routeData)

  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    createPermissionRoute.makeRoute(featureFactoryConfig) ~
      getPermissionRoute.makeRoute(featureFactoryConfig) ~
      updatePermissionRoute.makeRoute(featureFactoryConfig) ~
      deletePermissionRoute.makeRoute(featureFactoryConfig)
}
