/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.PathMatcher
import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM.*

/**
 * Provides a routing function for API routes that deal with groups.
 */

final case class GroupsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator & StringFormatter & MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with GroupsADMJsonProtocol {

  private val groupsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "groups")

  override def makeRoute: Route =
    deleteGroup()

  /**
   * Deletes a group (sets status to false).
   */
  private def deleteGroup(): Route = path(groupsBasePath / Segment) { groupIri =>
    delete { ctx =>
      val task = for {
        r           <- getIriUserUuid(groupIri, ctx)
        changeStatus = ChangeGroupApiRequestADM(status = Some(false))
      } yield GroupChangeStatusRequestADM(r.iri, changeStatus, r.user, r.uuid)
      runJsonRouteZ(task, ctx)
    }
  }
}
