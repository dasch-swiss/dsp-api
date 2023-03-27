/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import zio._

import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

/**
 * Provides routes to delete list items.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
final case class DeleteListItemsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator with StringFormatter with MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with ListADMJsonProtocol {

  val listsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")

  def makeRoute: Route =
    deleteListItem() ~
      canDeleteList() ~
      deleteListNodeComments()

  /* delete list (i.e. root node) or a child node which should also delete its children */
  private def deleteListItem(): Route = path(listsBasePath / Segment) { iri =>
    delete {
      /* delete a list item root node or child if unused */
      requestContext =>
        val requestTask =
          RouteUtilADM.getIriUserUuid(iri, requestContext).map(r => ListItemDeleteRequestADM(r.iri, r.user, r.uuid))
        RouteUtilADM.runJsonRouteZ(requestTask, requestContext)
    }
  }

  /**
   * Checks if a list can be deleted (none of its nodes is used in data).
   */
  private def canDeleteList(): Route =
    path(listsBasePath / "candelete" / Segment) { iri =>
      get { requestContext =>
        val requestTask = RouteUtilADM.getIriUser(iri, requestContext).map(r => CanDeleteListRequestADM(r.iri, r.user))
        RouteUtilADM.runJsonRouteZ(requestTask, requestContext)
      }
    }

  /**
   * Deletes all comments from requested list node (only child).
   */
  private def deleteListNodeComments(): Route =
    path(listsBasePath / "comments" / Segment) { iri =>
      delete { requestContext =>
        val requestTask =
          RouteUtilADM.getIriUser(iri, requestContext).map(r => ListNodeCommentsDeleteRequestADM(r.iri, r.user))
        RouteUtilADM.runJsonRouteZ(requestTask, requestContext)
      }
    }
}
