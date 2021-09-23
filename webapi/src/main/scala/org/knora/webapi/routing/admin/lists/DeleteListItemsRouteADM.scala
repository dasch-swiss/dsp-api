/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

package org.knora.webapi.routing.admin.lists

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.{Feature, FeatureFactoryConfig}
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}

import scala.concurrent.Future

object DeleteListItemsRouteADM {
  val ListsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")
}

/**
 * A [[Feature]] that provides routes to delete list items.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
class DeleteListItemsRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Feature
    with Authenticator
    with ListADMJsonProtocol {

  import DeleteListItemsRouteADM._

  def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    deleteListItem(featureFactoryConfig)

  /* delete list (i.e. root node) or a child node which should also delete its children */
  private def deleteListItem(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
    delete {
      /* delete a list item root node or child if unused */
      requestContext =>
        val nodeIri =
          stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid list item Iri: $iri"))

        val requestMessage: Future[ListItemDeleteRequestADM] = for {
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)
        } yield ListItemDeleteRequestADM(
          nodeIri = nodeIri,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
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
