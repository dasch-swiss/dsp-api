/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import io.swagger.annotations._

import javax.ws.rs.Path
import scala.concurrent.Future

import dsp.errors.BadRequestException
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

object GetListItemsRouteADM {
  val ListsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")
}

/**
 * Provides routes to get list items.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
@Api(value = "lists", produces = "application/json")
@Path("/admin/lists")
class GetListItemsRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with ListADMJsonProtocol {

  import GetListItemsRouteADM._

  def makeRoute(): Route =
    getLists() ~
      getListNode() ~
      getListOrNodeInfo("infos") ~
      getListOrNodeInfo("nodes") ~
      getListInfo()

  @ApiOperation(value = "Get lists", nickname = "getlists", httpMethod = "GET", response = classOf[ListsGetResponseADM])
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  /**
   * Returns all lists optionally filtered by project.
   */
  private def getLists(): Route = path(ListsBasePath) {
    get {
      parameters("projectIri".?) { maybeProjectIri: Option[IRI] => requestContext =>
        val projectIri =
          stringFormatter.validateAndEscapeOptionalIri(
            maybeProjectIri,
            throw BadRequestException(s"Invalid param project IRI: $maybeProjectIri")
          )

        val requestMessage: Future[ListsGetRequestADM] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext
                            )
        } yield ListsGetRequestADM(
          projectIri = projectIri,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }
  }

  @Path("/{IRI}")
  @ApiOperation(value = "Get a list", nickname = "getlist", httpMethod = "GET", response = classOf[ListGetResponseADM])
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  /**
   * Returns a list node, root or child, with children (if exist).
   */
  private def getListNode(): Route = path(ListsBasePath / Segment) { iri =>
    get { requestContext =>
      val listIri =
        stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

      val requestMessage: Future[ListGetRequestADM] = for {
        requestingUser <- getUserADM(
                            requestContext = requestContext
                          )
      } yield ListGetRequestADM(
        iri = listIri,
        requestingUser = requestingUser
      )

      RouteUtilADM.runJsonRoute(
        requestMessageF = requestMessage,
        requestContext = requestContext,
        settings = settings,
        appActor = appActor,
        log = log
      )
    }
  }

  /**
   * Returns basic information about list node, root or child, w/o children (if exist).
   */
  private def getListOrNodeInfo(routeSwitch: String): Route =
    path(ListsBasePath / routeSwitch / Segment) { iri =>
      get { requestContext =>
        val listIri =
          stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))
        val requestMessage: Future[ListNodeInfoGetRequestADM] = for {
          requestingUser <- getUserADM(requestContext)
        } yield ListNodeInfoGetRequestADM(
          iri = listIri,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
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
    path(ListsBasePath / Segment / "info") { iri =>
      get { requestContext =>
        val listIri =
          stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

        val requestMessage: Future[ListNodeInfoGetRequestADM] = for {
          requestingUser <- getUserADM(requestContext)
        } yield ListNodeInfoGetRequestADM(
          iri = listIri,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }
}
