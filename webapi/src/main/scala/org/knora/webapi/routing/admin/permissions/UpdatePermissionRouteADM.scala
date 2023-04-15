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
final case class UpdatePermissionRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator with StringFormatter with MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with PermissionsADMJsonProtocol {

  val permissionsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "permissions")

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    updatePermissionGroup() ~
      updatePermissionHasPermissions() ~
      updatePermissionResourceClass() ~
      updatePermissionProperty()

  /**
   * Update a permission's group
   */
  private def updatePermissionGroup(): Route =
    path(permissionsBasePath / Segment / "group") { iri =>
      put {
        entity(as[ChangePermissionGroupApiRequestADM]) { apiRequest => ctx =>
          val task = getIriUserUuid(iri, ctx)
            .map(r => PermissionChangeGroupRequestADM(r.iri, apiRequest, r.user, r.uuid))
          runJsonRouteZ(task, ctx)
        }
      }
    }

  /**
   * Update a permission's set of hasPermissions.
   */
  private def updatePermissionHasPermissions(): Route =
    path(permissionsBasePath / Segment / "hasPermissions") { iri =>
      put {
        entity(as[ChangePermissionHasPermissionsApiRequestADM]) { apiRequest => requestContext =>
          val task = getIriUserUuid(iri, requestContext)
            .map(r => PermissionChangeHasPermissionsRequestADM(r.iri, apiRequest, r.user, r.uuid))
          runJsonRouteZ(task, requestContext)
        }
      }
    }

  /**
   * Update a doap permission by setting it for a new resource class
   */
  private def updatePermissionResourceClass(): Route =
    path(permissionsBasePath / Segment / "resourceClass") { iri =>
      put {
        entity(as[ChangePermissionResourceClassApiRequestADM]) { apiRequest => requestContext =>
          val task = getIriUserUuid(iri, requestContext)
            .map(r => PermissionChangeResourceClassRequestADM(r.iri, apiRequest, r.user, r.uuid))
          runJsonRouteZ(task, requestContext)
        }
      }
    }

  /**
   * Update a doap permission by setting it for a new property class
   */
  private def updatePermissionProperty(): Route =
    path(permissionsBasePath / Segment / "property") { iri =>
      put {
        entity(as[ChangePermissionPropertyApiRequestADM]) { apiRequest => requestContext =>
          val task = getIriUserUuid(iri, requestContext)
            .map(r => PermissionChangePropertyRequestADM(r.iri, apiRequest, r.user, r.uuid))
          runJsonRouteZ(task, requestContext)
        }
      }
    }
}
