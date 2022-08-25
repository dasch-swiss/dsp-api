/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.permissions

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import io.swagger.annotations._

import java.util.UUID
import javax.ws.rs.Path

import dsp.errors.BadRequestException
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

@Api(value = "permissions", produces = "application/json")
@Path("/admin/permissions")
class UpdatePermissionRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with PermissionsADMJsonProtocol {

  val PermissionsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "permissions")

  /**
   * Returns the route.
   */
  override def makeRoute(): Route =
    updatePermissionGroup() ~
      updatePermissionHasPermissions() ~
      updatePermissionResourceClass() ~
      updatePermissionProperty()

  /**
   * Update a permission's group
   */
  private def updatePermissionGroup(): Route =
    path(PermissionsBasePath / Segment / "group") { iri =>
      put {
        entity(as[ChangePermissionGroupApiRequestADM]) { apiRequest => requestContext =>
          val permissionIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid permission IRI: $iri"))

          val requestMessage = for {
            requestingUser <- getUserADM(requestContext)
          } yield PermissionChangeGroupRequestADM(
            permissionIri = permissionIri,
            changePermissionGroupRequest = apiRequest,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
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

  /**
   * Update a permission's set of hasPermissions.
   */
  private def updatePermissionHasPermissions(): Route =
    path(PermissionsBasePath / Segment / "hasPermissions") { iri =>
      put {
        entity(as[ChangePermissionHasPermissionsApiRequestADM]) { apiRequest => requestContext =>
          val permissionIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid permission IRI: $iri"))

          val requestMessage = for {
            requestingUser <- getUserADM(requestContext)
          } yield PermissionChangeHasPermissionsRequestADM(
            permissionIri = permissionIri,
            changePermissionHasPermissionsRequest = apiRequest,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
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

  /**
   * Update a doap permission by setting it for a new resource class
   */
  private def updatePermissionResourceClass(): Route =
    path(PermissionsBasePath / Segment / "resourceClass") { iri =>
      put {
        entity(as[ChangePermissionResourceClassApiRequestADM]) { apiRequest => requestContext =>
          val permissionIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid permission IRI: $iri"))

          val requestMessage = for {
            requestingUser <- getUserADM(requestContext)
          } yield PermissionChangeResourceClassRequestADM(
            permissionIri = permissionIri,
            changePermissionResourceClassRequest = apiRequest,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
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

  /**
   * Update a doap permission by setting it for a new property class
   */
  private def updatePermissionProperty(): Route =
    path(PermissionsBasePath / Segment / "property") { iri =>
      put {
        entity(as[ChangePermissionPropertyApiRequestADM]) { apiRequest => requestContext =>
          val permissionIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid permission IRI: $iri"))

          val requestMessage = for {
            requestingUser <- getUserADM(requestContext)
          } yield PermissionChangePropertyRequestADM(
            permissionIri = permissionIri,
            changePermissionPropertyRequest = apiRequest,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
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
}
