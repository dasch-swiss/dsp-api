/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import zhttp.http._
import zio.ZLayer

import org.knora.webapi.core._

/**
 * The accumulated routes
 *
 * @param healthRoute
 */
final case class ApiRoutesWithZIOHttp(
  healthRoute: HealthRouteWithZIOHttp
) {
  // adds up all the routes
  val routes: HttpApp[State, Nothing] =
    healthRoute.route // TODO add more routes here with `++ projectRoutes.routes`

}

/**
 * The layer providing all instantiated routes
 */
object ApiRoutesWithZIOHttp {
  val layer: ZLayer[HealthRouteWithZIOHttp, Nothing, ApiRoutesWithZIOHttp] =
    ZLayer.fromFunction(ApiRoutesWithZIOHttp.apply _)
}
