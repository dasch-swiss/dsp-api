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
import org.knora.webapi.routing.admin.lists._

/**
 * Provides an akka-http-routing function for API routes that deal with lists.
 */
class ListsRouteADM(routeData: KnoraRouteData, appConfig: AppConfig) extends KnoraRoute(routeData, appConfig) {
  private val getNodeRoute: GetListItemsRouteADM       = new GetListItemsRouteADM(routeData, appConfig)
  private val createNodeRoute: CreateListItemsRouteADM = new CreateListItemsRouteADM(routeData, appConfig)
  private val deleteNodeRoute: DeleteListItemsRouteADM = new DeleteListItemsRouteADM(routeData, appConfig)
  private val updateNodeRoute: UpdateListItemsRouteADM = new UpdateListItemsRouteADM(routeData, appConfig)

  override def makeRoute: Route =
    getNodeRoute.makeRoute ~
      createNodeRoute.makeRoute ~
      deleteNodeRoute.makeRoute ~
      updateNodeRoute.makeRoute
}
