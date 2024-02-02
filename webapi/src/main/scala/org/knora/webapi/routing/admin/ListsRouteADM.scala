/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import org.apache.pekko
import zio.*

import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.routing
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.admin.lists.*

import pekko.http.scaladsl.server.Directives.*
import pekko.http.scaladsl.server.Route

/**
 * Provides an pekko-http-routing function for API routes that deal with lists.
 */
final case class ListsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[routing.Authenticator & StringFormatter & MessageRelay]
) extends KnoraRoute(routeData, runtime) {
  override def makeRoute: Route = CreateListItemsRouteADM(routeData, runtime).makeRoute
}
