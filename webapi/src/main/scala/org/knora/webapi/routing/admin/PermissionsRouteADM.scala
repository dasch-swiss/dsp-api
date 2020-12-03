/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
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
  private val getPermissionRoute: GetPermissionsRouteADM = new GetPermissionsRouteADM(routeData)
  private val updatePermissionRoute: UpdatePermissionRouteADM = new UpdatePermissionRouteADM(routeData)

  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = {
    createPermissionRoute.makeRoute(featureFactoryConfig) ~
      getPermissionRoute.makeRoute(featureFactoryConfig) ~
      updatePermissionRoute.makeRoute(featureFactoryConfig)
  }
}
