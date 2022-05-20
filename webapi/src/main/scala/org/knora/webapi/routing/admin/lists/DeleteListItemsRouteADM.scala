/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.Feature
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

import java.util.UUID
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
    deleteListItem(featureFactoryConfig) ~
      canDeleteList(featureFactoryConfig) ~
      deleteListNodeComments(featureFactoryConfig)

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

  /**
   * Checks if a list can be deleted (none of its nodes is used in data).
   */
  private def canDeleteList(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / "candelete" / Segment) { iri =>
      get { requestContext =>
        val listIri =
          stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid list IRI: $iri"))

        val requestMessage: Future[CanDeleteListRequestADM] = for {
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)
        } yield CanDeleteListRequestADM(
          iri = listIri,
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

  /**
   * Deletes all comments from requested list node (only child).
   */
  private def deleteListNodeComments(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / "deletecomments" / Segment) { iri =>
      delete { requestContext =>
        val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid list IRI: $iri"))

        val requestMessage: Future[ListNodeCommentsDeleteRequestADM] =
          for {
            requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          } yield ListNodeCommentsDeleteRequestADM(
            iri = listIri,
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
