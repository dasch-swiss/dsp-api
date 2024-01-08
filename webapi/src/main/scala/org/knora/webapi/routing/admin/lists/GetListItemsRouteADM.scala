/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import org.apache.pekko
import zio.*

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.IRI
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM.*

import pekko.http.scaladsl.server.Directives.*
import pekko.http.scaladsl.server.PathMatcher
import pekko.http.scaladsl.server.Route

/**
 * Provides routes to get list items.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
final case class GetListItemsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator & MessageRelay & StringFormatter]
) extends KnoraRoute(routeData, runtime)
    with ListADMJsonProtocol {

  val listsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")

  def makeRoute: Route =
    getLists ~
      getListNode ~
      getListOrNodeInfo("infos") ~
      getListOrNodeInfo("nodes") ~
      getListInfo

  /**
   * Returns all lists optionally filtered by project.
   */
  private def getLists: Route = path(listsBasePath) {
    get {
      parameters("projectIri".?) { (maybeProjectIri: Option[IRI]) => requestContext =>
        val task = for {
          iri <- ZIO.foreach(maybeProjectIri)(iri =>
                   Iri
                     .validateAndEscapeIri(iri)
                     .toZIO
                     .orElseFail(BadRequestException(s"Invalid param project IRI: $iri"))
                 )
          user <- Authenticator.getUserADM(requestContext)
        } yield ListsGetRequestADM(iri, user)
        runJsonRouteZ(task, requestContext)
      }
    }
  }

  /**
   * Returns a list node, root or child, with children (if exist).
   */
  private def getListNode: Route = path(listsBasePath / Segment) { iri =>
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
  private def getListInfo: Route =
    //  Brought from new lists route implementation, has the e functionality as getListOrNodeInfo
    path(listsBasePath / Segment / "info") { iri =>
      get { ctx =>
        val task = getIriUser(iri, ctx).map(r => ListNodeInfoGetRequestADM(r.iri, r.user))
        runJsonRouteZ(task, ctx)
      }
    }
}
