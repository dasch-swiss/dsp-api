/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import scala.concurrent.Future

import dsp.errors.BadRequestException
import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.listsmessages.ListGetRequestV2
import org.knora.webapi.messages.v2.responder.listsmessages.NodeGetRequestV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilV2

/**
 * Provides a function for API routes that deal with lists and nodes.
 */
class ListsRouteV2(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator {

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    getList() ~
      getNode()

  private def getList(): Route = path("v2" / "lists" / Segment) { lIri: String =>
    get {
      /* return a list (a graph with all list nodes) */
      requestContext =>
        val requestMessage: Future[ListGetRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
          listIri: IRI = stringFormatter.validateAndEscapeIri(
                           lIri,
                           throw BadRequestException(s"Invalid list IRI: '$lIri'")
                         )
        } yield ListGetRequestV2(
          listIri = listIri,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
    }
  }

  private def getNode(): Route = path("v2" / "node" / Segment) { nIri: String =>
    get {
      /* return a list node */
      requestContext =>
        val requestMessage: Future[NodeGetRequestV2] = for {
          requestingUser <- getUserADM(
                              requestContext = requestContext,
                              routeData.appConfig
                            )
          nodeIri: IRI = stringFormatter.validateAndEscapeIri(
                           nIri,
                           throw BadRequestException(s"Invalid list IRI: '$nIri'")
                         )
        } yield NodeGetRequestV2(
          nodeIri = nodeIri,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteWithFuture(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appConfig = routeData.appConfig,
          appActor = appActor,
          log = log,
          targetSchema = ApiV2Complex,
          schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
        )
    }
  }
}
