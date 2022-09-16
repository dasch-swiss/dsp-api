/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route

import scala.concurrent.Future

import dsp.errors.BadRequestException
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

/**
 * Provides routes to get list items.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
class GetListItemsRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
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
          stringFormatter.validateAndEscapeOptionalIri(
            maybeProjectIri,
            throw BadRequestException(s"Invalid param project IRI: $maybeProjectIri")
          )

        val requestMessage: Future[ListsGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
        } yield ListsGetRequestADM(
          projectIri = projectIri,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }
  }

  /**
   * Returns a list node, root or child, with children (if exist).
   */
  private def getListNode(): Route = path(listsBasePath / Segment) { iri =>
    get { requestContext =>
      val listIri =
        stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

      val requestMessage: Future[ListGetRequestADM] = for {
        requestingUser <- getUserADM(
                            requestContext = requestContext,
                            routeData.appConfig
                          )
      } yield ListGetRequestADM(
        iri = listIri,
        requestingUser = requestingUser
      )

      RouteUtilADM.runJsonRoute(
        requestMessageF = requestMessage,
        requestContext = requestContext,
        appActor = appActor,
        log = log
      )
    }
  }

  /**
   * Returns basic information about list node, root or child, w/o children (if exist).
   */
  private def getListOrNodeInfo(routeSwitch: String): Route =
    path(listsBasePath / routeSwitch / Segment) { iri =>
      get { requestContext =>
        val listIri =
          stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))
        val requestMessage: Future[ListNodeInfoGetRequestADM] = for {
          requestingUser <- getUserADM(requestContext, routeData.appConfig)
        } yield ListNodeInfoGetRequestADM(
          iri = listIri,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns basic information about a node, root or child, w/o children.
   */
  private def getListInfo(): Route =
    //  Brought from new lists route implementation, has the e functionality as getListOrNodeInfo
    path(listsBasePath / Segment / "info") { iri =>
      get { requestContext =>
        val listIri =
          stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

        val requestMessage: Future[ListNodeInfoGetRequestADM] = for {
          requestingUser <- getUserADM(requestContext, routeData.appConfig)
        } yield ListNodeInfoGetRequestADM(
          iri = listIri,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }
}
