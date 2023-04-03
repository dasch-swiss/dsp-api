/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import zio._

import scala.concurrent.Future

import dsp.errors.BadRequestException
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.v2.responder.listsmessages.ListGetRequestV2
import org.knora.webapi.messages.v2.responder.listsmessages.NodeGetRequestV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilV2

/**
 * Provides a function for API routes that deal with lists and nodes.
 */
final case class ListsRouteV2(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[AppConfig with Authenticator with MessageRelay]
) extends KnoraRoute(routeData, runtime) {

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
          requestingUser <- getUserADM(requestContext)
          listIri: IRI = stringFormatter.validateAndEscapeIri(
                           lIri,
                           throw BadRequestException(s"Invalid list IRI: '$lIri'")
                         )
        } yield ListGetRequestV2(
          listIri = listIri,
          requestingUser = requestingUser
        )
        RouteUtilV2.runRdfRouteF(requestMessage, requestContext)
    }
  }

  private def getNode(): Route = path("v2" / "node" / Segment) { nIri: String =>
    get {
      /* return a list node */
      requestContext =>
        val requestMessage: Future[NodeGetRequestV2] = for {
          requestingUser <- getUserADM(requestContext)
          nodeIri: IRI = stringFormatter.validateAndEscapeIri(
                           nIri,
                           throw BadRequestException(s"Invalid list IRI: '$nIri'")
                         )
        } yield NodeGetRequestV2(
          nodeIri = nodeIri,
          requestingUser = requestingUser
        )

        RouteUtilV2.runRdfRouteF(requestMessage, requestContext)
    }
  }
}
