/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.permissions

import org.apache.pekko
import zio.*

import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM.*

import pekko.http.scaladsl.server.Directives.*
import pekko.http.scaladsl.server.PathMatcher
import pekko.http.scaladsl.server.Route

final case class DeletePermissionRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator & StringFormatter & MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with PermissionsADMJsonProtocol {

  val permissionsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "permissions")

  /**
   * Returns the route.
   */
  override def makeRoute: Route = deletePermission()

  /**
   * Delete a permission
   */
  private def deletePermission(): Route =
    path(permissionsBasePath / Segment) { iri =>
      delete { ctx =>
        val task = getUserUuid(ctx).map(r => PermissionDeleteRequestADM(iri, r.user, r.uuid))
        runJsonRouteZ(task, ctx)
      }
    }
}
