/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
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

package org.knora.webapi.routing.v2

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import org.knora.webapi._
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.v2.responder.listsmessages.{ListGetRequestV2, NodeGetRequestV2}
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV2}

import scala.concurrent.Future

/**
 * Provides a function for API routes that deal with lists and nodes.
 */
class ListsRouteV2(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

  /**
   * Returns the route.
   */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    getList(featureFactoryConfig) ~
      getNode(featureFactoryConfig)

  private def getList(featureFactoryConfig: FeatureFactoryConfig): Route = path("v2" / "lists" / Segment) {
    lIri: String =>
      get {
        /* return a list (a graph with all list nodes) */
        requestContext =>
          val requestMessage: Future[ListGetRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
            listIri: IRI = stringFormatter.validateAndEscapeIri(
              lIri,
              throw BadRequestException(s"Invalid list IRI: '$lIri'")
            )
          } yield ListGetRequestV2(
            listIri = listIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log,
            targetSchema = ApiV2Complex,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
      }
  }

  private def getNode(featureFactoryConfig: FeatureFactoryConfig): Route = path("v2" / "node" / Segment) {
    nIri: String =>
      get {
        /* return a list node */
        requestContext =>
          val requestMessage: Future[NodeGetRequestV2] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
            nodeIri: IRI = stringFormatter.validateAndEscapeIri(
              nIri,
              throw BadRequestException(s"Invalid list IRI: '$nIri'")
            )
          } yield NodeGetRequestV2(
            nodeIri = nodeIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
          )

          RouteUtilV2.runRdfRouteWithFuture(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log,
            targetSchema = ApiV2Complex,
            schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
          )
      }
  }
}
