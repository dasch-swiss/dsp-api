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

import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

object GetPermissionsRouteADM {
  val PermissionsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "permissions")
}

@Api(value = "permissions", produces = "application/json")
@Path("/admin/permissions")
class GetPermissionsRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with PermissionsADMJsonProtocol {

  import GetPermissionsRouteADM._

  /**
   * Returns the route.
   */
  override def makeRoute(): Route =
    getAdministrativePermissionForProjectGroup() ~
      getAdministrativePermissionsForProject() ~
      getDefaultObjectAccessPermissionsForProject() ~
      getPermissionsForProject()

  private def getAdministrativePermissionForProjectGroup(): Route =
    path(PermissionsBasePath / "ap" / Segment / Segment) { (projectIri, groupIri) =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(requestContext)
        } yield AdministrativePermissionForProjectGroupGetRequestADM(projectIri, groupIri, requestingUser)

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }

  private def getAdministrativePermissionsForProject(): Route =
    path(PermissionsBasePath / "ap" / Segment) { projectIri =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(requestContext)
        } yield AdministrativePermissionsForProjectGetRequestADM(
          projectIri = projectIri,
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

  private def getDefaultObjectAccessPermissionsForProject(): Route =
    path(PermissionsBasePath / "doap" / Segment) { projectIri =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(requestContext)
        } yield DefaultObjectAccessPermissionsForProjectGetRequestADM(
          projectIri = projectIri,
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

  private def getPermissionsForProject(): Route =
    path(PermissionsBasePath / Segment) { projectIri =>
      get { requestContext =>
        val requestMessage = for {
          requestingUser <- getUserADM(requestContext)
        } yield PermissionsForProjectGetRequestADM(
          projectIri = projectIri,
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
