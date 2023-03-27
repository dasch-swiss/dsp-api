/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.permissions

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import zio._

import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM._

final case class CreatePermissionRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator with StringFormatter with MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with PermissionsADMJsonProtocol {

  val permissionsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "permissions")

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    createAdministrativePermission() ~
      createDefaultObjectAccessPermission()

  /**
   * Create a new administrative permission
   */
  private def createAdministrativePermission(): Route =
    path(permissionsBasePath / "ap") {
      post {
        entity(as[CreateAdministrativePermissionAPIRequestADM]) { apiRequest => ctx =>
          val task = getUserUuid(ctx).map(r => AdministrativePermissionCreateRequestADM(apiRequest, r.user, r.uuid))
          runJsonRouteZ(task, ctx)
        }
      }
    }

  /**
   * Create default object access permission
   */
  private def createDefaultObjectAccessPermission(): Route =
    path(permissionsBasePath / "doap") {
      post {
        entity(as[CreateDefaultObjectAccessPermissionAPIRequestADM]) { apiRequest => requestContext =>
          val task = getUserUuid(requestContext).map(r =>
            DefaultObjectAccessPermissionCreateRequestADM(apiRequest, r.user, r.uuid)
          )
          runJsonRouteZ(task, requestContext)
        }
      }
    }
}
