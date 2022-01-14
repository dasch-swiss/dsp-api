/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.sipimessages.SipiFileInfoGetRequestADM
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}

/**
 * Provides a routing function for the API that Sipi connects to.
 */
class SipiRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

  /**
   * A routing function for the API that Sipi connects to.
   */
  /**
   * Returns the route.
   */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    path("admin" / "files" / Segments(2)) { projectIDAndFile: Seq[String] =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
          projectID = stringFormatter.validateProjectShortcode(
            projectIDAndFile.head,
            throw BadRequestException(s"Invalid project ID: '${projectIDAndFile.head}'")
          )
          filename = stringFormatter.toSparqlEncodedString(
            projectIDAndFile(1),
            throw BadRequestException(s"Invalid filename: '${projectIDAndFile(1)}'")
          )
          _ = println(s"/admin/files route called for filename $filename")
        } yield SipiFileInfoGetRequestADM(
          projectID = projectID,
          filename = filename,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

}
