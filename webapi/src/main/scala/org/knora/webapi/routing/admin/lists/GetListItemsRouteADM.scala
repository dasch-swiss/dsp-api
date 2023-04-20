/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import zio._

import dsp.errors.BadRequestException
import org.knora.webapi.IRI
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM._

/**
 * Provides routes to get list items.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
final case class GetListItemsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator with MessageRelay with StringFormatter]
) extends KnoraRoute(routeData, runtime)
    with ListADMJsonProtocol {

  val listsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")

  def makeRoute: Route =
    getLists() ~
      getListNode() ~
      getListOrNodeInfo("infos") ~
      getListOrNodeInfo("nodes") ~
      getListInfo()

  /**
   * Returns all lists optionally filtered by project.
   */
  private def getLists(): Route = path(listsBasePath) {
    get {
      parameters("projectIri".?) { maybeProjectIri: Option[IRI] => requestContext =>
        val projectIri =
          maybeProjectIri match {
            case None => None
            case Some(value) =>
              stringFormatter
                .validateAndEscapeIri(value)
                .toOption
                .orElse(throw BadRequestException(s"Invalid param project IRI: $value"))
          }

        val requestTask = Authenticator.getUserADM(requestContext).map(ListsGetRequestADM(projectIri, _))
        runJsonRouteZ(requestTask, requestContext)
      }
    }
  }

  /**
   * Returns a list node, root or child, with children (if exist).
   */
  private def getListNode(): Route = path(listsBasePath / Segment) { iri =>
    get { ctx =>
      val task = getIriUser(iri, ctx).map(r => ListGetRequestADM(r.iri, r.user))
      runJsonRouteZ(task, ctx)
    }
  }

  /**
   * Returns basic information about list node, root or child, w/o children (if exist).
   */
  private def getListOrNodeInfo(routeSwitch: String): Route =
    path(listsBasePath / routeSwitch / Segment) { iri =>
      get { ctx =>
        val task = getIriUser(iri, ctx).map(r => ListNodeInfoGetRequestADM(r.iri, r.user))
        runJsonRouteZ(task, ctx)
      }
    }

  /**
   * Returns basic information about a node, root or child, w/o children.
   */
  private def getListInfo(): Route =
    //  Brought from new lists route implementation, has the e functionality as getListOrNodeInfo
    path(listsBasePath / Segment / "info") { iri =>
      get { ctx =>
        val task = getIriUser(iri, ctx).map(r => ListNodeInfoGetRequestADM(r.iri, r.user))
        runJsonRouteZ(task, ctx)
      }
    }
}
