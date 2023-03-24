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

final case class GetPermissionsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator with StringFormatter with MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with PermissionsADMJsonProtocol {

  val permissionsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "permissions")

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    getAdministrativePermissionForProjectGroup() ~
      getAdministrativePermissionsForProject() ~
      getDefaultObjectAccessPermissionsForProject() ~
      getPermissionsForProject()

  private def getAdministrativePermissionForProjectGroup(): Route =
    path(permissionsBasePath / "ap" / Segment / Segment) { (projectIri, groupIri) =>
      get { requestContext =>
        val task = Authenticator
          .getUserADM(requestContext)
          .map(AdministrativePermissionForProjectGroupGetRequestADM(projectIri, groupIri, _))
        runJsonRouteZ(task, requestContext)
      }
    }

  private def getAdministrativePermissionsForProject(): Route =
    path(permissionsBasePath / "ap" / Segment) { projectIri =>
      get { requestContext =>
        val task = getUserUuid(requestContext)
          .map(r => AdministrativePermissionsForProjectGetRequestADM(projectIri, r.user, r.uuid))
        runJsonRouteZ(task, requestContext)
      }
    }

  private def getDefaultObjectAccessPermissionsForProject(): Route =
    path(permissionsBasePath / "doap" / Segment) { projectIri =>
      get { requestContext =>
        val task = getUserUuid(requestContext)
          .map(r => DefaultObjectAccessPermissionsForProjectGetRequestADM(projectIri, r.user, r.uuid))
        runJsonRouteZ(task, requestContext)
      }
    }

  private def getPermissionsForProject(): Route =
    path(permissionsBasePath / Segment) { projectIri =>
      get { requestContext =>
        val task = getUserUuid(requestContext)
          .map(r => PermissionsForProjectGetRequestADM(projectIri, r.user, r.uuid))
        runJsonRouteZ(task, requestContext)
      }
    }
}
