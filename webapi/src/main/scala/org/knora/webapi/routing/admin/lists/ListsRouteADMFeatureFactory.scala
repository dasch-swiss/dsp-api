/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import akka.http.scaladsl.server.Route
import org.knora.webapi.feature.{FeatureFactory, FeatureFactoryConfig}
import org.knora.webapi.routing.{KnoraRouteData, KnoraRouteFactory}

/**
 * A [[FeatureFactory]] that constructs list admin routes.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the routes.
 */
class ListsRouteADMFeatureFactory(routeData: KnoraRouteData) extends KnoraRouteFactory(routeData) with FeatureFactory {

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
  def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    if (featureFactoryConfig.getToggle("new-list-admin-routes").isEnabled) {
      newListsRouteADMFeature.makeRoute(featureFactoryConfig)
    } else {
      oldListsRouteADMFeature.makeRoute(featureFactoryConfig)
    }
}
