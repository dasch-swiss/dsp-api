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
import org.knora.webapi.routing.admin.lists._
import org.knora.webapi.routing.{KnoraRoute, KnoraRouteData}

/**
  * Provides an akka-http-routing function for API routes that deal with lists.
  */
class ListsRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) {
  private val featureFactory: ListsRouteADMFeatureFactory = new ListsRouteADMFeatureFactory(routeData)
  private val deleteNodeRoute: DeleteListItemsRouteADM = new DeleteListItemsRouteADM(routeData)
  private val updateNodeRoute: UpdateListItemsRouteADM = new UpdateListItemsRouteADM(routeData)

  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = {
    featureFactory.makeRoute(featureFactoryConfig) ~
      deleteNodeRoute.makeRoute(featureFactoryConfig) ~
      updateNodeRoute.makeRoute(featureFactoryConfig)
  }
}
