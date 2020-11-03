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

import akka.http.scaladsl.server.Route
import org.knora.webapi.feature.{FeatureFactory, FeatureFactoryConfig}
import org.knora.webapi.routing.{KnoraRouteData, KnoraRouteFactory}

/**
 * A [[FeatureFactory]] that constructs list admin routes.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the routes.
 */
class ListsRouteADMFeatureFactory(routeData: KnoraRouteData) extends KnoraRouteFactory(routeData)
    with FeatureFactory {

    /**
     * The old lists route feature.
     */
    private val oldListsRouteADMFeature = new OldListsRouteADMFeature(routeData)

    /**
     * The new lists route feature.
     */
    private val newListsRouteADMFeature = new NewListsRouteADMFeature(routeData)

    /**
     * Returns a lists route reflecting the specified feature factory configuration.
     *
     * @param featureFactoryConfig a [[FeatureFactoryConfig]].
     * @return a lists route.
     */
    def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route = {
        if (featureFactoryConfig.getToggle("new-list-admin-routes").isEnabled) {
            newListsRouteADMFeature.makeRoute(featureFactoryConfig)
        } else {
            oldListsRouteADMFeature.makeRoute(featureFactoryConfig)
        }
    }
}
