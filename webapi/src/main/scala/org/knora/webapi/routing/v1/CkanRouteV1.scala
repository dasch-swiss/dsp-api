/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v1

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.v1.responder.ckanmessages.CkanRequestV1
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV1}

/**
 * A route used to serve data to CKAN. It is used be the Ckan instance running under http://data.humanities.ch.
 */
class CkanRouteV1(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

  /**
   * Returns the route.
   */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    path("v1" / "ckan") {
      get { requestContext =>
        val requestMessage = for {
          userProfile <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
          params = requestContext.request.uri.query().toMap
          project: Option[Seq[String]] = params.get("project").map(_.split(",").toSeq)
          limit: Option[Int] = params.get("limit").map(_.toInt)
          info: Boolean = params.getOrElse("info", false) == true
        } yield CkanRequestV1(
          projects = project,
          limit = limit,
          info = info,
          featureFactoryConfig = featureFactoryConfig,
          userProfile = userProfile
        )

        RouteUtilV1.runJsonRouteWithFuture(
          requestMessage,
          requestContext,
          settings,
          responderManager,
          log
        )
      }
    }
}
