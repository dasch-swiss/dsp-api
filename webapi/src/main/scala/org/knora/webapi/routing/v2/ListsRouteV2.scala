/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.v2

import org.apache.pekko
import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.listsmessages.ListGetRequestV2
import org.knora.webapi.messages.v2.responder.listsmessages.NodeGetRequestV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.RouteUtilZ

import pekko.http.scaladsl.server.Directives._
import pekko.http.scaladsl.server.Route

/**
 * Provides a function for API routes that deal with lists and nodes.
 */
final case class ListsRouteV2()(
  private implicit val runtime: Runtime[AppConfig & Authenticator & StringFormatter & MessageRelay],
) {

  def makeRoute: Route = getList() ~ getNode()

  private def getList(): Route = path("v2" / "lists" / Segment) { (lIri: String) =>
    get {
      /* return a list (a graph with all list nodes) */
      requestContext =>
        val message = for {
          requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
          listIri        <- RouteUtilZ.validateAndEscapeIri(lIri, s"Invalid list IRI: '$lIri'")
        } yield ListGetRequestV2(listIri, requestingUser)
        RouteUtilV2.runRdfRouteZ(message, requestContext)
    }
  }

  private def getNode(): Route = path("v2" / "node" / Segment) { (nIri: String) =>
    get {
      /* return a list node */
      requestContext =>
        val message = for {
          requestingUser <- ZIO.serviceWithZIO[Authenticator](_.getUserADM(requestContext))
          nodeIri        <- RouteUtilZ.validateAndEscapeIri(nIri, s"Invalid list IRI: '$nIri'")
        } yield NodeGetRequestV2(nodeIri, requestingUser)
        RouteUtilV2.runRdfRouteZ(message, requestContext)
    }
  }
}
