/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.PathMatcher
import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionGroupApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionHasPermissionsApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionPropertyApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionResourceClassApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionChangeGroupRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionChangeHasPermissionsRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionChangePropertyRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionChangeResourceClassRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsADMJsonProtocol
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM.getIriUserUuid
import org.knora.webapi.routing.RouteUtilADM.runJsonRouteZ

/**
 * Provides an pekko-http-routing function for API routes that deal with permissions.
 */
final case class PermissionsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator & StringFormatter & MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with PermissionsADMJsonProtocol {

  private val permissionsBase: PathMatcher[Unit] = PathMatcher("admin" / "permissions")

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
    path(permissionsBase / Segment / "group") { iri =>
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
    path(permissionsBase / Segment / "hasPermissions") { iri =>
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
    path(permissionsBase / Segment / "resourceClass") { iri =>
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
    path(permissionsBase / Segment / "property") { iri =>
      put {
        entity(as[ChangePermissionPropertyApiRequestADM]) { apiRequest => requestContext =>
          val task = getIriUserUuid(iri, requestContext)
            .map(r => PermissionChangePropertyRequestADM(r.iri, apiRequest, r.user, r.uuid))
          runJsonRouteZ(task, requestContext)
        }
      }
    }
}
