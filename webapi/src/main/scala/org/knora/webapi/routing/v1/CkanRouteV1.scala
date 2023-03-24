/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import zio._

import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.v1.responder.ckanmessages.CkanRequestV1
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilV1

/**
 * A route used to serve data to CKAN. It is used be the Ckan instance running under http://data.humanities.ch.
 */
final case class CkanRouteV1(
  private val routeData: KnoraRouteData,
  override implicit protected val runtime: Runtime[Authenticator with MessageRelay]
) extends KnoraRoute(routeData, runtime) {

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    path("v1" / "ckan") {
      get { requestContext =>
        val requestTask = for {
          userProfile <- Authenticator.getUserADM(requestContext)
          params       = requestContext.request.uri.query().toMap
          project      = params.get("project").map(_.split(",").toSeq)
          limit        = params.get("limit").map(_.toInt)
          info         = params.getOrElse("info", false) == true
        } yield CkanRequestV1(project, limit, info, userProfile)
        RouteUtilV1.runJsonRouteZ(requestTask, requestContext)
      }
    }
}
